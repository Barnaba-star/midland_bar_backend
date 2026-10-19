package com.midland.bar.Bar.Supplier;

import com.midland.bar.Bar.Dto.StockReceiptDTO;
import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Model.StockReceipt;
import com.midland.bar.Bar.Repository.BarServiceRepository;
import com.midland.bar.Bar.Repository.StockReceiptRepository;
import com.midland.bar.Bar.Service.BillPaymentService;
import com.midland.bar.Bar.Service.CashUpService;
import com.midland.bar.Bar.Service.StockService;
import com.midland.bar.Bar.Service.WorkShiftService;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Notification.Sms.Service.SmsService;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Suppliers (owner, 2026-10-19): who the bar buys from, purchase orders,
 * receiving against them (the store goes up like any delivery), what is
 * owed to each supplier and paying it, and what each product was bought at.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierEntryRepository entryRepository;
    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final BarServiceRepository barServiceRepository;
    private final StockReceiptRepository stockReceiptRepository;
    private final StockService stockService;
    private final WorkShiftService workShiftService;
    private final ManunuziService manunuziService;
    private final SupplierDeliveryItemRepository deliveryItemRepository;

    // ---------------------------------------------------------------- suppliers

    public ResponseList<Map<String, Object>> suppliers() {
        String b = LoggerUser.getBranchUIDOrMain();
        Map<String, Long> owed = new HashMap<>();
        for (Object[] r : entryRepository.balances(b))
            owed.put((String) r[0], ((Number) r[1]).longValue());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Supplier s : supplierRepository.findInBranch(b))
            out.add(row(s, owed.getOrDefault(s.getUid(), 0L)));
        out.sort(Comparator.comparingLong((Map<String, Object> r) -> -(Long) r.get("owed")).thenComparing(r -> String.valueOf(r.get("name"))));
        return new ResponseList<>(out);
    }

    @Transactional
    public Response<Map<String, Object>> saveSupplier(String uid, Map<String, Object> body) {
        String b = LoggerUser.getBranchUIDOrMain();
        Supplier s = uid == null ? new Supplier() : supplierRepository.findInBranch(uid, b).orElse(null);
        if (s == null)
            return new Response<>("Supplier not found");
        String name = str(body.get("name"));
        if (name == null || name.length() < 2)
            return new Response<>("Enter the supplier's name");
        String phone = str(body.get("phone"));
        if (phone != null) {
            phone = SmsService.normalise(phone);
            if (phone == null)
                return new Response<>("Enter a valid phone number, e.g. 0712 345 678");
        }
        s.setName(cut(name.toUpperCase(), 100));
        s.setPhone(phone);
        s.setContactPerson(cut(str(body.get("contactPerson")), 80));
        s.setNote(cut(str(body.get("note")), 300));
        if (uid != null)
            s.update();
        Supplier saved = supplierRepository.save(s);
        return new Response<>(row(saved, entryRepository.balanceOf(saved.getUid())));
    }

    public Response<Map<String, Object>> ledger(String uid) {
        String b = LoggerUser.getBranchUIDOrMain();
        Supplier s = supplierRepository.findInBranch(uid, b).orElse(null);
        if (s == null)
            return new Response<>("Supplier not found");
        Map<String, Object> out = row(s, entryRepository.balanceOf(uid));
        out.put("entries", entryRepository.ledger(uid, b));
        return new Response<>(out);
    }

    /**
     * Paying a supplier. Out of the drawer (fromDrawer) needs the payer's shift
     * open, as any payout, and comes off their cash-up by its method.
     */
    @Transactional
    public Response<Map<String, Object>> pay(Map<String, Object> body) {
        String b = LoggerUser.getBranchUIDOrMain();
        String uid = str(body.get("supplierUid"));
        Supplier s = uid == null ? null : supplierRepository.findInBranch(uid, b).orElse(null);
        if (s == null)
            return new Response<>("Supplier not found");
        long amount = num(body.get("amount"));
        if (amount <= 0)
            return new Response<>("Enter the amount");
        long owed = entryRepository.balanceOf(uid);
        if (amount > owed)
            return new Response<>("The bar owes " + s.getName() + " only " + owed);
        String method = str(body.get("method"));
        method = method == null ? "cash" : method.toLowerCase();
        if (!BillPaymentService.METHODS.contains(method))
            return new Response<>("Unknown payment method: " + method);
        boolean fromDrawer = !"false".equalsIgnoreCase(String.valueOf(body.get("fromDrawer")));
        if (fromDrawer)
            workShiftService.requireOpenForPayout();
        // Oldest deliveries first. Their Manunuzi was used when they arrived;
        // older ones (from before that) take it now, as they always did.
        String payer = fromDrawer ? LoggerUser.getEmail() : null;
        long left = amount;
        long covered = 0;
        for (SupplierDeliveryItem item : deliveryItemRepository.unpaid(uid)) {
            if (left <= 0)
                break;
            long part = Math.min(left, item.getAmount() - item.getPaid());
            // A delivery whose Manunuzi was used when it arrived is only settled here.
            if (item.getPotCovered() == null) {
                String label = "Msambazaji " + s.getName() + (item.getPoCode() != null ? " · " + item.getPoCode() : "") + " · " + item.getServiceName();
                covered += manunuziService.spend(LoggerUser.getBranchUIDOrMain(), item.getServiceUid(), part, label, method, payer, s.getName());
            }
            item.setPaid(item.getPaid() + part);
            deliveryItemRepository.save(item);
            left -= part;
        }
        SupplierEntry e = new SupplierEntry();
        e.setSupplierUid(uid);
        e.setKind(SupplierEntry.PAYMENT);
        e.setAmount(amount);
        e.setOutsidePot(amount - covered);
        e.setMethod(method);
        e.setFromDrawer(fromDrawer);
        e.setDetail(cut(str(body.get("note")), 300));
        e.setEntryBy(LoggerUser.getEmail());
        e.setEntryByName(CashUpService.nameOf(LoggerUser.getUser()));
        e.setEntryAt(LocalDateTime.now());
        entryRepository.save(e);
        log.info("{} paid supplier {} {} {}", LoggerUser.getEmail(), uid, amount, method);
        return ledger(uid);
    }

    // ---------------------------------------------------------------- purchase orders

    public ResponseList<Map<String, Object>> orders() {
        String b = LoggerUser.getBranchUIDOrMain();
        Map<String, String> names = new HashMap<>();
        supplierRepository.findInBranch(b).forEach(s -> names.put(s.getUid(), s.getName()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PurchaseOrder po : poRepository.findInBranch(b))
            out.add(orderRow(po, names.get(po.getSupplierUid())));
        return new ResponseList<>(out);
    }

    @Transactional
    public Response<Map<String, Object>> createOrder(Map<String, Object> body) {
        String b = LoggerUser.getBranchUIDOrMain();
        String supplierUid = str(body.get("supplierUid"));
        Supplier s = supplierUid == null ? null : supplierRepository.findInBranch(supplierUid, b).orElse(null);
        if (s == null)
            return new Response<>("Choose the supplier");
        List<?> items = body.get("lines") instanceof List<?> l ? l : List.of();
        if (items.isEmpty())
            return new Response<>("Add what is ordered");
        PurchaseOrder po = new PurchaseOrder();
        po.setCode(String.format("PO-%03d", poRepository.countInBranch(b) + 1));
        po.setSupplierUid(supplierUid);
        po.setNote(cut(str(body.get("note")), 300));
        String exp = str(body.get("expectedOn"));
        po.setExpectedOn(exp == null ? null : LocalDate.parse(exp));
        po.setOrderedBy(LoggerUser.getEmail());
        po.setOrderedAt(LocalDateTime.now());
        long total = 0;
        List<PurchaseOrderLine> lines = new ArrayList<>();
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> m))
                continue;
            String serviceUid = str(m.get("serviceUid"));
            BarServiceEntity svc = serviceUid == null ? null : barServiceRepository.findById(serviceUid)
                    .filter(x -> b.equals(x.getBranchUid())).orElse(null);
            if (svc == null)
                return new Response<>("A product on the order was not found");
            if (!Boolean.TRUE.equals(svc.getTrackStock()))
                return new Response<>(svc.getServiceName() + " is not counted in the store");
            int packs = (int) num(m.get("packs"));
            if (packs < 1)
                return new Response<>("Enter how many of " + svc.getServiceName());
            long price = m.get("packPrice") == null ? (svc.getBuyingPrice() == null ? 0 : svc.getBuyingPrice()) : num(m.get("packPrice"));
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setServiceUid(svc.getUid());
            line.setServiceName(svc.getServiceName());
            line.setPacks(packs);
            line.setPackPrice((int) price);
            lines.add(line);
            total += price * packs;
        }
        // Within each product's Manunuzi budget, unless a CEO or manager chose to go over.
        // A whole pack is bought to replace what was sold (owner, 2026-10-19):
        // savings that cover part of a pack allow that pack, so going over by
        // less than one pack is not over the budget.
        Map<String, Long> byProduct = new LinkedHashMap<>();
        Map<String, Long> packOf = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (PurchaseOrderLine l : lines) {
            byProduct.merge(l.getServiceUid(), (long) l.getPacks() * l.getPackPrice(), Long::sum);
            packOf.merge(l.getServiceUid(), (long) l.getPackPrice(), Math::max);
            names.put(l.getServiceUid(), l.getServiceName());
        }
        Map<String, long[]> budgets = manunuziService.budgets(b);
        List<String> over = new ArrayList<>();
        byProduct.forEach((svc, value) -> {
            long free = budgets.getOrDefault(svc, new long[4])[3];
            if (value > free && (free <= 0 || value - free >= packOf.get(svc)))
                over.add(names.get(svc) + " (Tshs " + value + " > Manunuzi Tshs " + Math.max(0, free) + ")");
        });
        boolean allowOver = "true".equalsIgnoreCase(String.valueOf(body.get("allowOverBudget")));
        if (!over.isEmpty() && !allowOver)
            return new Response<>("Over the Manunuzi budget: " + String.join(", ", over));
        po.setOverBudget(!over.isEmpty());
        po.setTotal(total);
        PurchaseOrder saved = poRepository.save(po);
        lines.forEach(l -> l.setPoUid(saved.getUid()));
        lineRepository.saveAll(lines);
        log.info("{} ordered {} from supplier {} ({})", LoggerUser.getEmail(), saved.getCode(), supplierUid, total);
        return order(saved.getUid());
    }

    public Response<Map<String, Object>> order(String uid) {
        String b = LoggerUser.getBranchUIDOrMain();
        PurchaseOrder po = poRepository.findById(uid).filter(p -> b.equals(p.getBranchUid())).orElse(null);
        if (po == null)
            return new Response<>("Order not found");
        Map<String, Object> out = orderRow(po, supplierRepository.findById(po.getSupplierUid()).map(Supplier::getName).orElse(null));
        out.put("lines", lineRepository.findByPo(uid));
        return new Response<>(out);
    }

    /**
     * Goods arrived on an order: each line's packs into the store (at the
     * price given, else the ordered one), what arrived on the supplier's
     * account in one line, and the order PARTIAL or RECEIVED.
     */
    @Transactional
    public Response<Map<String, Object>> receive(String uid, Map<String, Object> body) {
        String b = LoggerUser.getBranchUIDOrMain();
        PurchaseOrder po = poRepository.findForUpdate(uid, b).orElse(null);
        if (po == null)
            return new Response<>("Order not found");
        if (PurchaseOrder.RECEIVED.equals(po.getStatus()) || PurchaseOrder.CANCELLED.equals(po.getStatus()))
            return new Response<>("Order " + po.getCode() + " is " + po.getStatus().toLowerCase());
        Map<String, PurchaseOrderLine> lines = new HashMap<>();
        lineRepository.findByPo(uid).forEach(l -> lines.put(l.getUid(), l));
        List<?> items = body.get("lines") instanceof List<?> l ? l : List.of();
        long total = 0;
        List<String> detail = new ArrayList<>();
        List<SupplierDeliveryItem> delivered = new ArrayList<>();
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> m))
                continue;
            PurchaseOrderLine line = lines.get(str(m.get("lineUid")));
            int packs = (int) num(m.get("packs"));
            if (line == null || packs <= 0)
                continue;
            int left = line.getPacks() - line.getReceivedPacks();
            if (packs > left)
                throw new BusinessException("Only " + left + " of " + line.getServiceName() + " are still to come on " + po.getCode());
            StockReceiptDTO dto = new StockReceiptDTO();
            dto.setBarServiceUID(line.getServiceUid());
            dto.setPacks(packs);
            dto.setLooseUnits(0);
            dto.setPackPrice(m.get("packPrice") == null ? line.getPackPrice() : (int) num(m.get("packPrice")));
            dto.setSupplierUid(po.getSupplierUid());
            dto.setNote(po.getCode());
            dto.setOnOrder(true);
            dto.setPoCode(po.getCode());
            Response<StockReceipt> r = stockService.addStock(dto);
            if (r.getData() == null)
                throw new BusinessException(r.getMessage());
            total += r.getData().getTotalCost();
            detail.add(line.getServiceName() + " x " + packs);
            SupplierDeliveryItem item = new SupplierDeliveryItem();
            item.setSupplierUid(po.getSupplierUid());
            item.setServiceUid(line.getServiceUid());
            item.setServiceName(line.getServiceName());
            item.setPoCode(po.getCode());
            item.setAmount(r.getData().getTotalCost());
            item.setReceivedAt(LocalDateTime.now());
            delivered.add(item);
            line.setReceivedPacks(line.getReceivedPacks() + packs);
            lineRepository.save(line);
        }
        if (total == 0 && detail.isEmpty())
            return new Response<>("Enter what arrived");
        SupplierEntry e = new SupplierEntry();
        e.setSupplierUid(po.getSupplierUid());
        e.setKind(SupplierEntry.DELIVERY);
        e.setAmount(total);
        e.setPoCode(po.getCode());
        e.setDetail(cut(String.join(", ", detail), 300));
        e.setEntryBy(LoggerUser.getEmail());
        e.setEntryByName(CashUpService.nameOf(LoggerUser.getUser()));
        e.setEntryAt(LocalDateTime.now());
        SupplierEntry savedEntry = entryRepository.save(e);
        String supplierName = supplierRepository.findById(po.getSupplierUid()).map(Supplier::getName).orElse("");
        delivered.forEach(i -> {
            i.setEntryUid(savedEntry.getUid());
            manunuziService.takeOnDelivery(b, i, supplierName);
        });
        deliveryItemRepository.saveAll(delivered);
        boolean all = lines.values().stream().allMatch(l -> l.getReceivedPacks() >= l.getPacks());
        po.setStatus(all ? PurchaseOrder.RECEIVED : PurchaseOrder.PARTIAL);
        po.update();
        poRepository.save(po);
        log.info("{} received {} on {} ({})", LoggerUser.getEmail(), String.join(", ", detail), po.getCode(), total);
        return order(uid);
    }

    @Transactional
    public Response<Map<String, Object>> cancel(String uid) {
        PurchaseOrder po = poRepository.findForUpdate(uid, LoggerUser.getBranchUIDOrMain()).orElse(null);
        if (po == null)
            return new Response<>("Order not found");
        if (!PurchaseOrder.OPEN.equals(po.getStatus()))
            return new Response<>("Only an order nothing has arrived on can be cancelled");
        po.setStatus(PurchaseOrder.CANCELLED);
        po.update();
        poRepository.save(po);
        return order(uid);
    }

    /** What a product was bought at, newest first: when, the pack price, from whom. */
    public ResponseList<Map<String, Object>> priceHistory(String serviceUid) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StockReceipt r : stockReceiptRepository.priceHistory(serviceUid, LoggerUser.getBranchUIDOrMain())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("receivedAt", r.getReceivedAt());
            m.put("packPrice", r.getPackPrice());
            m.put("packs", r.getPacks());
            m.put("unitsAdded", r.getUnitsAdded());
            m.put("supplier", r.getSupplier());
            m.put("poCode", r.getPoCode());
            out.add(m);
        }
        return new ResponseList<>(out);
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> row(Supplier s, long owed) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("uid", s.getUid());
        r.put("name", s.getName());
        r.put("phone", s.getPhone());
        r.put("contactPerson", s.getContactPerson());
        r.put("note", s.getNote());
        r.put("owed", owed);
        return r;
    }

    private static Map<String, Object> orderRow(PurchaseOrder po, String supplierName) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("uid", po.getUid());
        r.put("code", po.getCode());
        r.put("supplierUid", po.getSupplierUid());
        r.put("supplierName", supplierName);
        r.put("status", po.getStatus());
        r.put("total", po.getTotal());
        r.put("expectedOn", po.getExpectedOn());
        r.put("note", po.getNote());
        r.put("orderedAt", po.getOrderedAt());
        r.put("overBudget", po.getOverBudget());
        return r;
    }

    private static String str(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }

    private static long num(Object v) {
        String s = str(v);
        if (s == null)
            return 0;
        try {
            return Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
