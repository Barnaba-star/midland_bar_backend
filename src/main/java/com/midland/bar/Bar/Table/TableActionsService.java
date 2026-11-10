package com.midland.bar.Bar.Table;

import com.midland.bar.Bar.Alert.OwnerAlertService;
import com.midland.bar.Bar.Live.LiveEvents;
import com.midland.bar.Bar.Service.LoyaltyService;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * What a customer does from a table's QR window besides reading the menu
 * (owner, 2026-11-09): call the waiter, ask for the bill, write to the
 * manager, check their ofa / visits. And the bar's side: the open calls and
 * the feedback list. Customer calls are limited per table so a prank can't
 * flood the bar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableActionsService {

    /** Calls older than this are not "open" any more - the night is over. */
    private static final int CALL_HOURS = 6;
    /** One call of a kind per table per this many seconds. */
    private static final int CALL_REST_SECONDS = 60;
    /** At most this many messages from one table per 10 minutes. */
    private static final int FEEDBACK_PER_10_MIN = 5;
    public static final String TOPIC_CALLS = "calls";

    private final BarTableRepository tableRepository;
    private final TableCallRepository callRepository;
    private final CustomerFeedbackRepository feedbackRepository;
    private final LiveEvents liveEvents;
    private final OwnerAlertService ownerAlertService;
    private final LoyaltyService loyaltyService;
    private final TableOrderRepository orderRepository;
    private final BarTableInfoRepository infoRepository;
    private final com.midland.bar.Bar.Repository.BarServiceRepository barServiceRepository;
    private final com.midland.bar.Bar.Repository.BarStaffRepository barStaffRepository;
    private final com.midland.bar.Bar.Repository.SalesOpenedRepository salesOpenedRepository;
    private final com.midland.bar.Bar.Service.StaffOrderService staffOrderService;
    private final com.midland.bar.Bar.Service.StaffSellService staffSellService;
    private final BarTableService tableService;
    private final com.midland.bar.Bar.Repository.BarSalesRepository barSalesRepository;
    private final com.midland.bar.Bar.Repository.BillPaymentRepository billPaymentRepository;

    /** Bill copies a table may look up in 10 minutes - numbers are short, so no guessing through them. */
    static final int BILL_LOOKUPS_PER_10_MIN = 10;
    /** A paid bill can still be seen this long after it was paid. */
    static final int PAID_BILL_HOURS = 6;
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.Deque<Long>> billLookups = new java.util.concurrent.ConcurrentHashMap<>();
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    /** At most this many orders from one table per 10 minutes. */
    private static final int ORDERS_PER_10_MIN = 6;

    private Optional<BarTable> table(String token) {
        String key = token == null ? "" : token.trim().toUpperCase();
        return key.length() == 10 ? tableRepository.findByToken(key) : Optional.empty();
    }

    // ------------------------------------------------------------ customer side

    public Response<Map<String, Object>> call(String token, String rawKind) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        String kind = TableCall.BILL.equalsIgnoreCase(rawKind) ? TableCall.BILL : TableCall.WAITER;
        LocalDateTime now = LocalDateTime.now();
        Optional<TableCall> open = callRepository.findOpenFor(t.getUid(), kind, now.minusHours(CALL_HOURS));
        if (open.isPresent()) {
            // Already waiting: a second tap within a minute is refused, later it just stays open.
            if (open.get().getCalledAt().isAfter(now.minusSeconds(CALL_REST_SECONDS)))
                return new Response<>("TOO_SOON");
            return new Response<>(Map.of("kind", kind, "waiting", true));
        }
        TableCall c = new TableCall();
        c.setBranchUid(t.getBranchUid());
        c.setTableUid(t.getUid());
        c.setTableName(t.getName());
        c.setKind(kind);
        c.setCalledAt(now);
        callRepository.save(c);
        liveEvents.publish(t.getBranchUid(), TOPIC_CALLS);
        log.info("{} called ({}) in branch {}", t.getName(), kind, t.getBranchUid());
        return new Response<>(Map.of("kind", kind, "waiting", true));
    }

    /**
     * The customer types their bill number (MK-1) and sees a copy of it - what
     * is on it, what is paid and what is left - and their waiter is told the
     * table asked for the bill (owner, 2026-11-10). Only this branch's bills,
     * unpaid or paid in the last few hours.
     */
    public Response<Map<String, Object>> billCopy(String token, String rawCode) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase().replaceAll("\\s+", "");
        if (code.isEmpty() || code.length() > 30)
            return new Response<>("BILL_NOT_FOUND");
        long now = System.currentTimeMillis();
        java.util.Deque<Long> tries = billLookups.computeIfAbsent(t.getUid(), k -> new java.util.ArrayDeque<>());
        synchronized (tries) {
            while (!tries.isEmpty() && tries.peekFirst() < now - 10 * 60_000L)
                tries.pollFirst();
            if (tries.size() >= BILL_LOOKUPS_PER_10_MIN)
                return new Response<>("TOO_MANY");
            tries.addLast(now);
        }
        LocalDateTime paidSince = LocalDateTime.now().minusHours(PAID_BILL_HOURS);
        List<com.midland.bar.Bar.Model.SalesOpened> found = salesOpenedRepository.findRecentByCode(code, t.getBranchUid(), java.time.LocalDate.now().minusDays(1));
        com.midland.bar.Bar.Model.SalesOpened bill = found.stream().filter(b -> "PENDING".equals(b.getPaymentStatus())).findFirst()
                .orElse(found.stream().filter(b -> "PAID".equals(b.getPaymentStatus()) && b.getPaidAt() != null && b.getPaidAt().isAfter(paidSince))
                        .findFirst().orElse(null));
        if (bill == null)
            return new Response<>("BILL_NOT_FOUND");

        // Each beer is added as its own line; the copy reads one line per item and
        // price ("6 x Castle Lite"), the free units of an offer on a line of their own.
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (var l : barSalesRepository.findBarSalesList(t.getBranchUid(), bill.getUid())) {
            int qty = l.getQuantity() == null ? 1 : l.getQuantity();
            int lineTotal = l.getLineTotal() == null ? 0 : l.getLineTotal();
            String offer = lineTotal == 0 && l.getPriceRuleName() != null ? l.getPriceRuleName() : null;
            String key = l.getServiceName() + "|" + l.getPrice() + "|" + (offer == null ? "" : offer);
            Map<String, Object> m = merged.computeIfAbsent(key, k -> {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("name", l.getServiceName());
                n.put("qty", 0);
                n.put("price", l.getPrice());
                n.put("total", 0);
                n.put("offer", offer);
                return n;
            });
            m.put("qty", (Integer) m.get("qty") + qty);
            m.put("total", (Integer) m.get("total") + lineTotal);
        }
        List<Map<String, Object>> lines = new ArrayList<>(merged.values());
        long total = barSalesRepository.billTotal(bill.getUid());
        long paid = billPaymentRepository.findByBill(bill.getUid(), t.getBranchUid()).stream()
                .mapToLong(p -> p.getAmount() == null ? 0 : p.getAmount()).sum();
        boolean isPaid = "PAID".equals(bill.getPaymentStatus());

        // Unpaid: the waiter is told this table wants its bill (a repeat within the minute is quiet).
        if (!isPaid) {
            Optional<TableCall> open = callRepository.findOpenFor(t.getUid(), TableCall.BILL, LocalDateTime.now().minusHours(CALL_HOURS));
            if (open.isEmpty()) {
                TableCall c = new TableCall();
                c.setBranchUid(t.getBranchUid());
                c.setTableUid(t.getUid());
                c.setTableName(t.getName());
                c.setKind(TableCall.BILL);
                c.setNote("Bill " + bill.getSalesCode() + (bill.getStaffName() == null ? "" : " (" + bill.getStaffName() + ")"));
                c.setCalledAt(LocalDateTime.now());
                callRepository.save(c);
                liveEvents.publish(t.getBranchUid(), TOPIC_CALLS);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", bill.getSalesCode());
        out.put("staffName", bill.getStaffName() != null ? bill.getStaffName() : bill.getOpenedByName());
        out.put("status", bill.getPaymentStatus());
        out.put("lines", lines);
        out.put("total", total);
        out.put("paid", isPaid ? total : paid);
        out.put("toPay", isPaid ? 0 : Math.max(0, total - paid));
        out.put("paidAt", bill.getPaidAt());
        return new Response<>(out);
    }

    public Response<Map<String, Object>> feedback(String token, Map<String, Object> body) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        String kind = String.valueOf(body.getOrDefault("kind", "ADVICE")).toUpperCase();
        if (!CustomerFeedback.KINDS.contains(kind))
            kind = "ADVICE";
        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        if (message.length() < 2)
            return new Response<>("Write your message");
        if (message.length() > 1000)
            message = message.substring(0, 1000);
        Integer rating = null;
        if (body.get("rating") instanceof Number n && n.intValue() >= 1 && n.intValue() <= 5)
            rating = n.intValue();
        LocalDateTime now = LocalDateTime.now();
        if (feedbackRepository.countFromTableSince(t.getUid(), now.minusMinutes(10)) >= FEEDBACK_PER_10_MIN)
            return new Response<>("TOO_MANY");
        CustomerFeedback f = new CustomerFeedback();
        f.setBranchUid(t.getBranchUid());
        f.setTableUid(t.getUid());
        f.setTableName(t.getName());
        f.setKind(kind);
        f.setMessage(message);
        f.setRating(rating);
        f.setCustomerName(trim(body.get("name"), 60));
        f.setCustomerPhone(trim(body.get("phone"), 20));
        f.setStaffName(waiterOf(t.getUid()));
        f.setSentAt(now);
        feedbackRepository.save(f);
        ownerAlertService.customerFeedback(t.getBranchUid(), t.getName(), kind, rating, message);
        return new Response<>(Map.of("sent", true));
    }

    /** Phone + PIN (the bar's Ofa za Wateja): what they have, as plain lines for the window. */
    public Response<Map<String, Object>> card(String token, Map<String, String> body) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        try {
            var c = loyaltyService.verify(body.get("phone"), body.get("pin"), t.getBranchUid());
            Map<String, Object> card = loyaltyService.card(c, t.getBranchUid());
            List<Map<String, String>> lines = new ArrayList<>();
            long offer = card.get("offerLeft") instanceof Number n ? n.longValue() : 0;
            lines.add(line("Ofa iliyobaki", "Tsh " + String.format("%,d", offer)));
            if (card.get("loyalty") instanceof Map<?, ?> l && Boolean.TRUE.equals(l.get("enabled"))) {
                lines.add(line("Ziara mwezi huu", l.get("visits") + " kati ya " + l.get("needed")));
                if (l.get("waiting") instanceof Number w && w.longValue() > 0)
                    lines.add(line("Bill zinazosubiri kulipwa", String.valueOf(w)));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", card.get("name"));
            out.put("lines", lines);
            return new Response<>(out);
        } catch (BusinessException e) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            return new Response<>(m.startsWith("Too many") ? "TOO_MANY_ATTEMPTS" : "WRONG_PHONE_OR_PIN");
        }
    }

    /** The waiter serving a table now: who took its latest order tonight. */
    private String waiterOf(String tableUid) {
        return orderRepository.findForTable(tableUid, LocalDateTime.now().minusHours(CALL_HOURS)).stream()
                .filter(o -> TableOrder.ACCEPTED.equals(o.getStatus()) && o.getStaffName() != null)
                .map(TableOrder::getStaffName).findFirst().orElse(null);
    }

    /** "Nimelipa": paid the bar's own number - the cashier sees it with the note and checks. */
    public Response<Map<String, Object>> paid(String token, Map<String, Object> body) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        String method = trim(body.get("method"), 20);
        String name = trim(body.get("name"), 60);
        String ref = trim(body.get("ref"), 40);
        String amount = trim(body.get("amount"), 15);
        if (name == null && ref == null)
            return new Response<>("Write your name or the payment reference");
        LocalDateTime now = LocalDateTime.now();
        Optional<TableCall> open = callRepository.findOpenFor(t.getUid(), TableCall.PAID, now.minusHours(CALL_HOURS));
        if (open.isPresent() && open.get().getCalledAt().isAfter(now.minusSeconds(CALL_REST_SECONDS)))
            return new Response<>("TOO_SOON");
        TableCall c = new TableCall();
        c.setBranchUid(t.getBranchUid());
        c.setTableUid(t.getUid());
        c.setTableName(t.getName());
        c.setKind(TableCall.PAID);
        c.setNote(String.join(" · ", java.util.stream.Stream.of(method, name, ref == null ? null : "ref " + ref,
                amount == null ? null : "Tsh " + amount).filter(Objects::nonNull).toList()));
        c.setCalledAt(now);
        callRepository.save(c);
        liveEvents.publish(t.getBranchUid(), TOPIC_CALLS);
        return new Response<>(Map.of("sent", true));
    }

    /** A customer's order: [{uid, qty}] from the menu. Waits for someone at the bar to take it. */
    @SuppressWarnings("unchecked")
    public Response<Map<String, Object>> placeOrder(String token, Map<String, Object> body) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        LocalDateTime now = LocalDateTime.now();
        if (orderRepository.countFromTableSince(t.getUid(), now.minusMinutes(10)) >= ORDERS_PER_10_MIN)
            return new Response<>("TOO_MANY");
        List<Map<String, Object>> raw = body.get("items") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        if (raw.isEmpty() || raw.size() > 30)
            return new Response<>("Choose what to order");
        List<Map<String, Object>> lines = new ArrayList<>();
        int total = 0;
        for (Map<String, Object> r : raw) {
            String uid = r.get("uid") == null ? null : r.get("uid").toString();
            int qty = r.get("qty") instanceof Number n ? n.intValue() : 0;
            if (uid == null || qty < 1 || qty > 20)
                return new Response<>("Choose between 1 and 20 of each");
            var service = barServiceRepository.findById(uid)
                    .filter(sv -> t.getBranchUid().equals(sv.getBranchUid()) && !"STOCK_ITEM".equals(sv.getKind())
                            && sv.getPrice() != null && sv.getPrice() > 0)
                    .orElse(null);
            if (service == null)
                return new Response<>("An item is no longer on the menu");
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("uid", service.getUid());
            line.put("name", service.getServiceName());
            line.put("qty", qty);
            line.put("price", service.getPrice());
            // Baridi / moto, as the customer chose on the drink (owner, 2026-11-09).
            String serving = asksServing(service.getCategory())
                    ? com.midland.bar.Bar.Model.StaffOrderLine.serving(r.get("serving") == null ? null : r.get("serving").toString())
                    : null;
            if (serving != null)
                line.put("serving", serving);
            lines.add(line);
            total += service.getPrice() * qty;
        }
        TableOrder o = new TableOrder();
        o.setBranchUid(t.getBranchUid());
        o.setTableUid(t.getUid());
        o.setTableName(t.getName());
        try {
            o.setLinesJson(JSON.writeValueAsString(lines));
        } catch (Exception e) {
            return new Response<>("Could not read the order");
        }
        o.setTotal(total);
        o.setNote(trim(body.get("note"), 200));
        o.setPlacedAt(now);
        orderRepository.save(o);
        liveEvents.publish(t.getBranchUid(), TOPIC_CALLS);
        log.info("{} ordered {} line(s), Tsh {}", t.getName(), lines.size(), total);
        return new Response<>(Map.of("uid", o.getUid(), "status", o.getStatus()));
    }

    /** Drinks are asked cold or warm - as on the waiters' picker: not food, shisha, pool or juice. */
    public static boolean asksServing(String category) {
        return !List.of("FOOD", "SHISHA", "POOL", "JUICE").contains(category == null ? "" : category.toUpperCase());
    }

    /** The table's orders tonight, newest first, and who serves it - the window shows their state. */
    public Response<Map<String, Object>> tableOrders(String token) {
        BarTable t = table(token).orElse(null);
        if (t == null)
            return new Response<>("TABLE_NOT_FOUND");
        List<Map<String, Object>> out = new ArrayList<>();
        for (TableOrder o : orderRepository.findForTable(t.getUid(), LocalDateTime.now().minusHours(CALL_HOURS))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", o.getUid());
            m.put("status", o.getStatus());
            m.put("total", o.getTotal());
            m.put("lines", lines(o));
            m.put("placedAt", o.getPlacedAt());
            m.put("staffName", o.getStaffName());
            m.put("rejectReason", o.getRejectReason());
            out.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orders", out);
        res.put("waiter", waiterOf(t.getUid()));
        return new Response<>(res);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> lines(TableOrder o) {
        try {
            return JSON.readValue(o.getLinesJson(), List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    // --------------------------------------------------------------- bar side

    /** Orders from tables waiting to be taken - shown with the calls. */
    public ResponseList<Map<String, Object>> pendingOrders() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TableOrder o : orderRepository.findPending(LoggerUser.getBranchUIDOrMain(), LocalDateTime.now().minusHours(CALL_HOURS))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", o.getUid());
            m.put("tableName", o.getTableName());
            m.put("total", o.getTotal());
            m.put("note", o.getNote());
            m.put("placedAt", o.getPlacedAt());
            m.put("lines", lines(o));
            out.add(m);
        }
        return new ResponseList<>(out);
    }

    /**
     * Take a table's order for a waiter: it goes on the waiter's unpaid bill
     * for that table (opened if none) and on to the counter / kitchen.
     */
    @org.springframework.transaction.annotation.Transactional
    public Response<TableOrder> acceptOrder(String uid, String staffUid) {
        String branch = LoggerUser.getBranchUIDOrMain();
        TableOrder o = orderRepository.findInBranch(uid, branch).orElse(null);
        if (o == null)
            return new Response<>("Order Not Found");
        if (!TableOrder.PENDING.equals(o.getStatus()))
            return new Response<>("This order was already " + o.getStatus().toLowerCase());
        // A waiter signed in with code + PIN takes it onto their own bill, never a colleague's.
        if (com.midland.bar.Config.Security.StaffSession.active())
            staffUid = com.midland.bar.Config.Security.StaffSession.staffUid();
        var staff = staffUid == null ? null : barStaffRepository.findBarStaffByUID(staffUid, branch).orElse(null);
        if (staff == null)
            return new Response<>("Choose the waiter");
        var bill = salesOpenedRepository.findOpenForTable(o.getTableUid(), staff.getUid(), branch).stream().findFirst().orElse(null);
        if (bill == null) {
            var dto = new com.midland.bar.Bar.Dto.StaffBillDTO();
            dto.setStaffCode(staff.getStaffCode());
            var opened = staffSellService.openBill(dto);
            if (opened.getData() == null)
                return new Response<>(opened.getMessage());
            bill = opened.getData();
            bill.setTableUid(o.getTableUid());
            bill.setTableName(o.getTableName());
            bill = salesOpenedRepository.save(bill);
        }
        List<com.midland.bar.Bar.Service.StaffOrderService.TableItem> items = new ArrayList<>();
        for (Map<String, Object> l : lines(o))
            items.add(new com.midland.bar.Bar.Service.StaffOrderService.TableItem(
                    String.valueOf(l.get("uid")), ((Number) l.get("qty")).intValue(),
                    l.get("serving") == null ? null : l.get("serving").toString()));
        staffOrderService.sendFromTable(bill, items, o.getTableName());
        o.setStatus(TableOrder.ACCEPTED);
        o.setDecidedAt(LocalDateTime.now());
        o.setDecidedBy(LoggerUser.getEmail());
        o.setStaffUid(bill.getStaffUid());
        o.setStaffName(bill.getStaffName());
        o.setBillUid(bill.getUid());
        o.setBillCode(bill.getSalesCode());
        orderRepository.save(o);
        liveEvents.publish(branch, "orders");
        liveEvents.publish(branch, "bills");
        liveEvents.publish(branch, TOPIC_CALLS);
        return new Response<>(o);
    }

    public Response<TableOrder> rejectOrder(String uid, String reason) {
        String branch = LoggerUser.getBranchUIDOrMain();
        TableOrder o = orderRepository.findInBranch(uid, branch).orElse(null);
        if (o == null)
            return new Response<>("Order Not Found");
        if (!TableOrder.PENDING.equals(o.getStatus()))
            return new Response<>("This order was already " + o.getStatus().toLowerCase());
        String why = trim(reason, 200);
        if (why == null)
            return new Response<>("Write the reason the customer will see");
        o.setStatus(TableOrder.REJECTED);
        o.setRejectReason(why);
        o.setDecidedAt(LocalDateTime.now());
        o.setDecidedBy(LoggerUser.getEmail());
        orderRepository.save(o);
        liveEvents.publish(branch, TOPIC_CALLS);
        return new Response<>(o);
    }

    /** The branch's info for customers (WiFi, hours, phone, events, payment numbers). */
    public Response<BarTableInfo> info() {
        return new Response<>(infoRepository.findInBranch(LoggerUser.getBranchUIDOrMain()).orElseGet(BarTableInfo::new));
    }

    public Response<BarTableInfo> saveInfo(Map<String, String> b) {
        BarTableInfo i = infoRepository.findInBranch(LoggerUser.getBranchUIDOrMain()).orElseGet(BarTableInfo::new);
        i.setWifiName(trim(b.get("wifiName"), 60));
        i.setWifiPassword(trim(b.get("wifiPassword"), 60));
        i.setHours(trim(b.get("hours"), 120));
        i.setPhone(trim(b.get("phone"), 30));
        i.setNote(trim(b.get("note"), 500));
        i.setPayMpesa(trim(b.get("payMpesa"), 60));
        i.setPayTigo(trim(b.get("payTigo"), 60));
        i.setPayAirtel(trim(b.get("payAirtel"), 60));
        i.setPayHalo(trim(b.get("payHalo"), 60));
        i.setPayBank(trim(b.get("payBank"), 120));
        tableService.forgetViews();
        return new Response<>(infoRepository.save(i));
    }

    // ---- calls (bar side)

    public ResponseList<TableCall> openCalls() {
        return new ResponseList<>(callRepository.findOpen(LoggerUser.getBranchUIDOrMain(), LocalDateTime.now().minusHours(CALL_HOURS)));
    }

    public Response<TableCall> done(String uid) {
        TableCall c = callRepository.findInBranch(uid, LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (c == null)
            return new Response<>("Call Not Found");
        if (c.getDoneAt() == null) {
            c.setDoneAt(LocalDateTime.now());
            c.setDoneBy(LoggerUser.getEmail());
            callRepository.save(c);
            liveEvents.publish(c.getBranchUid(), TOPIC_CALLS);
        }
        return new Response<>(c);
    }

    public ResponseList<CustomerFeedback> feedbackList(String filter) {
        LocalDateTime[] range = com.midland.bar.Bar.Service.ReportRange.of(filter);
        return new ResponseList<>(feedbackRepository.findIn(LoggerUser.getBranchUIDOrMain(), range[0], range[1]));
    }

    public Response<Long> unreadFeedback() {
        return new Response<>(feedbackRepository.countUnread(LoggerUser.getBranchUIDOrMain()));
    }

    public Response<CustomerFeedback> markRead(String uid) {
        CustomerFeedback f = feedbackRepository.findInBranch(uid, LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (f == null)
            return new Response<>("Feedback Not Found");
        if (f.getReadAt() == null) {
            f.setReadAt(LocalDateTime.now());
            f.setReadBy(LoggerUser.getEmail());
            feedbackRepository.save(f);
        }
        return new Response<>(f);
    }

    private static Map<String, String> line(String label, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        return m;
    }

    private static String trim(Object v, int max) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
