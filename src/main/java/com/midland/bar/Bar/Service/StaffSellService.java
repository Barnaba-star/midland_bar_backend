package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Dto.StaffBillDTO;
import com.midland.bar.Bar.Dto.StaffSellUnlockDTO;
import com.midland.bar.Bar.Repository.BillPaymentRepository;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Model.SalesOpened;
import com.midland.bar.Bar.Repository.BarStaffRepository;
import com.midland.bar.Bar.Repository.SalesOpenedRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Staff Sell: a staff member types their code and gets their own open bills,
 * or opens one - its code taken from theirs (K1-1, K1-2...). From there the bill is sold on, viewed and paid through the
 * same calls as the Sales page - only whose bill it is differs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffSellService {

    /** Selling and paying out need the login's shift open. */
    private final WorkShiftService workShiftService;
    private final BarStaffRepository barStaffRepository;
    private final SalesOpenedRepository salesOpenedRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StaffOrderService staffOrderService;
    private final com.midland.bar.Bar.Repository.BarSalesRepository barSalesRepository;
    private final BillPaymentService billPaymentService;

    /** Roles whose login may take the screen back from Staff Sell to POS. */
    private static final Set<String> UNLOCK_ROLES = Set.of("ROOT", "STAFF", "DIRECTOR", "CEO", "MANAGER");
    private static final int MAX_TRIES = 5;
    private static final long LOCK_SECONDS = 60;

    /** Wrong unlock attempts per branch: [count, locked-until epoch second]. Memory only - a restart clears it. */
    private final Map<String, long[]> unlockFails = new ConcurrentHashMap<>();

    /** Who the code belongs to, and their unpaid bills. */
    public Response<Map<String, Object>> findByCode(String code) {
        com.midland.bar.Config.Security.StaffSession.requireOwnCode(code);
        Optional<BarStaff> staff = byCode(code);
        if (staff.isEmpty())
            return new Response<>("No staff member has code " + clean(code));
        Map<String, Object> out = new LinkedHashMap<>();
        List<SalesOpened> bills = salesOpenedRepository.findOpenBillsByStaff(staff.get().getUid(), LoggerUser.getBranchUIDOrMain());
        out.put("staff", summary(staff.get()));
        out.put("bills", bills);
        // Written but not yet on the bill (waiting on the supervisor), and ones turned back.
        out.put("orders", staffOrderService.shownOn(bills.stream().map(SalesOpened::getUid).toList()));
        return new Response<>(out);
    }

    /**
     * A new bill for the staff member. Its code is theirs plus the lowest
     * number no unpaid bill is holding - K1-1, then K1-2 while K1-1 is open,
     * and K1-1 again once it is paid.
     */
    public Response<SalesOpened> openBill(StaffBillDTO dto) {
        com.midland.bar.Config.Security.StaffSession.requireOwnCode(dto == null ? null : dto.getStaffCode());
        // Opened offline: the device's uid for it (its orders point there). Sent twice, the same bill.
        if (dto.getClientUid() != null) {
            Optional<SalesOpened> existing = salesOpenedRepository.findById(dto.getClientUid());
            if (existing.isPresent())
                return new Response<>((SalesOpened) org.hibernate.Hibernate.unproxy(existing.get()));
        }
        workShiftService.requireOpen();
        Optional<BarStaff> staff = byCode(dto.getStaffCode());
        if (staff.isEmpty())
            return new Response<>("No staff member has code " + clean(dto.getStaffCode()));

        String prefix = staff.get().getStaffCode() + "-";
        Set<String> held = new HashSet<>(salesOpenedRepository.findOpenBillCodes(LoggerUser.getBranchUIDOrMain()));
        int n = 1;
        while (held.contains(prefix + n))
            n++;

        SalesOpened bill = new SalesOpened();
        if (dto.getClientUid() != null)
            bill.setUid(dto.getClientUid());
        bill.setCreatedAt(com.midland.bar.Utils.Offline.OfflineContext.today());
        bill.setSalesCode(prefix + n);
        bill.setStaffUid(staff.get().getUid());
        bill.setStaffName(fullName(staff.get()));
        bill.setStaffCode(staff.get().getStaffCode());
        SalesOpened saved = salesOpenedRepository.save(bill);
        log.info("{} opened bill {} for staff {}", LoggerUser.getEmail(), saved.getSalesCode(), staff.get().getStaffCode());
        return new Response<>(saved);
    }

    /**
     * What a staff member has to hand over, by payment method. Unpaid bills
     * count as cash unless noted "paid by phone" (then under that method);
     * bills the cashier already took are listed by how they were paid, from
     * the start of the branch's open shift (or today, with none open).
     */
    public Response<Map<String, Object>> handover(String code) {
        com.midland.bar.Config.Security.StaffSession.requireOwnCode(code);
        Optional<BarStaff> staff = byCode(code);
        if (staff.isEmpty())
            return new Response<>("No staff member has code " + clean(code));
        String branchUID = LoggerUser.getBranchUIDOrMain();

        Map<String, long[]> openByMethod = new LinkedHashMap<>();
        openByMethod.put("cash", new long[2]);
        List<Map<String, Object>> openBills = new ArrayList<>();
        long openTotal = 0;
        LocalDateTime sentAt = null;
        List<SalesOpened> open = salesOpenedRepository.findOpenBillsByStaff(staff.get().getUid(), branchUID);
        Map<String, Long> totals = billTotals(open);
        for (SalesOpened b : open) {
            // What paying will charge - the lines, not the stored figure.
            long amount = totals.getOrDefault(b.getUid(), 0L);
            String method = handoverMethod(b);
            if (b.getHandoverSentAt() != null && (sentAt == null || b.getHandoverSentAt().isAfter(sentAt)))
                sentAt = b.getHandoverSentAt();
            long[] m = openByMethod.computeIfAbsent(method, k -> new long[2]);
            m[0] += amount;
            m[1]++;
            openTotal += amount;
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("uid", b.getUid());
            line.put("salesCode", b.getSalesCode());
            line.put("amount", amount);
            line.put("method", method);
            line.put("payer", b.getPaymentNotePayer());
            line.put("sentAt", b.getHandoverSentAt());
            openBills.add(line);
        }

        LocalDateTime since = workShiftService.branchShiftStart(branchUID).orElse(LocalDate.now().atStartOfDay());
        Map<String, long[]> paidByMethod = new LinkedHashMap<>();
        long paidTotal = 0;
        for (Object[] r : billPaymentRepository.paidByStaffSince(branchUID, staff.get().getUid(), since)) {
            String method = r[0] == null ? "other" : ((String) r[0]).toLowerCase();
            long[] m = paidByMethod.computeIfAbsent(method, k -> new long[2]);
            m[0] += ((Number) r[1]).longValue();
            m[1] += ((Number) r[2]).longValue();
            paidTotal += ((Number) r[1]).longValue();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("staff", summary(staff.get()));
        out.put("since", since);
        out.put("sentAt", sentAt);
        out.put("open", Map.of("byMethod", methodRows(openByMethod), "total", openTotal, "bills", openBills));
        out.put("paid", Map.of("byMethod", methodRows(paidByMethod), "total", paidTotal));
        return new Response<>(out);
    }

    /** What paying each bill will charge, in one query. */
    private Map<String, Long> billTotals(List<SalesOpened> bills) {
        Map<String, Long> out = new java.util.HashMap<>();
        if (bills.isEmpty())
            return out;
        for (Object[] r : barSalesRepository.billTotals(bills.stream().map(SalesOpened::getUid).toList()))
            out.put((String) r[0], ((Number) r[1]).longValue());
        return out;
    }

    /** Where an unpaid bill counts at handover: the method of its "paid by phone" note, else cash. */
    private static String handoverMethod(SalesOpened bill) {
        String note = bill.getPaymentNoteMethod();
        return note == null || note.isBlank() ? "cash" : note.trim().toLowerCase();
    }

    /** The staff member is ready to hand over: their unpaid bills are marked for the cashier. */
    public Response<Integer> sendHandover(String code) {
        com.midland.bar.Config.Security.StaffSession.requireOwnCode(code);
        Optional<BarStaff> staff = byCode(code);
        if (staff.isEmpty())
            return new Response<>("No staff member has code " + clean(code));
        List<SalesOpened> bills = salesOpenedRepository.findOpenBillsByStaff(staff.get().getUid(), LoggerUser.getBranchUIDOrMain())
                .stream().filter(b -> barSalesRepository.billTotal(b.getUid()) > 0).toList();
        if (bills.isEmpty())
            return new Response<>("You have no bills to hand over");
        LocalDateTime now = LocalDateTime.now();
        bills.forEach(b -> b.setHandoverSentAt(now));
        salesOpenedRepository.saveAll(bills);
        log.info("{} sent {} bill(s) of staff {} for handover", LoggerUser.getEmail(), bills.size(), staff.get().getStaffCode());
        return new Response<>(bills.size());
    }

    /**
     * The cashier took one method's money at handover: every bill of the staff
     * member in that method is paid in full by it, all or none. The bills and
     * amounts must be the ones the summary showed - a bill opened, changed or
     * paid since means the cashier looks again.
     */
    @org.springframework.transaction.annotation.Transactional
    public Response<Map<String, Object>> receiveHandover(com.midland.bar.Bar.Dto.HandoverReceiveDTO dto) {
        if (com.midland.bar.Config.Security.StaffSession.active())
            throw new com.midland.bar.Utils.Exceptions.BusinessException("Only the cashier can receive a handover");
        workShiftService.requireOpen();
        Optional<BarStaff> staff = byCode(dto.getStaffCode());
        if (staff.isEmpty())
            return new Response<>("No staff member has code " + clean(dto.getStaffCode()));
        String method = dto.getMethod().trim().toLowerCase();
        if (!BillPaymentService.METHODS.contains(method))
            return new Response<>("Unknown payment method: " + dto.getMethod());

        Map<String, Long> now = new LinkedHashMap<>();
        List<SalesOpened> open = salesOpenedRepository.findOpenBillsByStaff(staff.get().getUid(), LoggerUser.getBranchUIDOrMain());
        Map<String, Long> totals = billTotals(open);
        for (SalesOpened b : open) {
            long amount = totals.getOrDefault(b.getUid(), 0L);
            if (amount > 0 && method.equals(handoverMethod(b)))
                now.put(b.getUid(), amount);
        }
        Map<String, Long> shown = new LinkedHashMap<>();
        dto.getBills().forEach(b -> shown.put(b.getUid(), b.getAmount()));
        if (!now.equals(shown))
            return new Response<>("The bills have changed since the summary was opened - refresh it and check again");

        long total = 0;
        for (Map.Entry<String, Long> e : now.entrySet()) {
            com.midland.bar.Bar.Dto.PayBillDTO pay = new com.midland.bar.Bar.Dto.PayBillDTO();
            pay.setSalesOpenedUID(e.getKey());
            com.midland.bar.Bar.Dto.PayBillDTO.Part part = new com.midland.bar.Bar.Dto.PayBillDTO.Part();
            part.setMethod(method);
            part.setAmount(e.getValue().intValue());
            pay.setPayments(List.of(part));
            Response<SalesOpened> paid = billPaymentService.payBill(pay);
            // One refused bill undoes the ones already paid here.
            if (paid.getData() == null)
                throw new com.midland.bar.Utils.Exceptions.BusinessException(paid.getMessage());
            total += e.getValue();
        }
        log.info("{} received {} {} from staff {} for {} bill(s)", LoggerUser.getEmail(), total, method, staff.get().getStaffCode(), now.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", method);
        out.put("bills", now.size());
        out.put("amount", total);
        return new Response<>(out);
    }

    private static List<Map<String, Object>> methodRows(Map<String, long[]> byMethod) {
        List<Map<String, Object>> rows = new ArrayList<>();
        byMethod.forEach((method, m) -> rows.add(Map.of("method", method, "amount", m[0], "bills", m[1])));
        return rows;
    }

    /** Hand the staff member's written orders to the supervisor - on switching staff, or Send. */
    public Response<Integer> sendOrders(String staffCode) {
        com.midland.bar.Config.Security.StaffSession.requireOwnCode(staffCode);
        workShiftService.requireOpen();
        Optional<BarStaff> staff = byCode(staffCode);
        if (staff.isEmpty())
            return new Response<>("No staff member has code " + clean(staffCode));
        return staffOrderService.send(staff.get());
    }

    private Optional<BarStaff> byCode(String code) {
        String c = StaffCodeService.normalise(code);
        if (c.isEmpty())
            return Optional.empty();
        Optional<BarStaff> exact = barStaffRepository.findByStaffCode(c, LoggerUser.getBranchUIDOrMain());
        if (exact.isPresent() || !c.matches("\\d+"))
            return exact;
        // "1" and "01" are 001 - staff will not always type the zeros.
        return barStaffRepository.findByStaffCode(String.format("%03d", Integer.parseInt(c)), LoggerUser.getBranchUIDOrMain());
    }

    private static String clean(String code) {
        return code == null ? "" : code.trim();
    }

    private static Map<String, Object> summary(BarStaff staff) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("uid", staff.getUid());
        s.put("staffCode", staff.getStaffCode());
        s.put("name", fullName(staff));
        s.put("category", staff.getBarCategory());
        return s;
    }

    static String fullName(BarStaff staff) {
        String first = staff.getFirstName() == null ? "" : staff.getFirstName().trim();
        String last = staff.getLastName() == null ? "" : staff.getLastName().trim();
        return (first + " " + last).trim();
    }

    /**
     * Leaving Staff Sell for POS takes a manager's login, so a staff member
     * cannot wander back into POS - where the manager's rights are - by a
     * stray tap. Any active user of this branch with a managing role will do,
     * not only whoever signed in. Five wrong tries lock it for a minute.
     */
    public Response<Boolean> unlock(StaffSellUnlockDTO dto) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        String key = branchUID;
        long now = java.time.Instant.now().getEpochSecond();
        long[] fails = unlockFails.get(key);
        if (fails != null && fails[1] > now)
            return new Response<>("Too many wrong tries - wait " + (fails[1] - now) + " seconds");

        User user = userRepository.findByUsernameForAuthentication(dto.getUsername().trim());
        boolean ok = user != null
                && passwordEncoder.matches(dto.getPassword(), user.getPassword())
                && !Boolean.TRUE.equals(user.getIsBlocked())
                && Boolean.TRUE.equals(user.getIsActive())
                && (Boolean.TRUE.equals(user.getIsRoot())
                    || (user.getBranch() != null && user.getBranch().getUid().equals(branchUID)
                        && user.getRoles() != null
                        && user.getRoles().stream().anyMatch(r -> UNLOCK_ROLES.contains(r.getCode()))));
        if (!ok) {
            long[] f = unlockFails.merge(key, new long[]{1, 0}, (a, b) -> new long[]{a[0] + 1, a[1]});
            if (f[0] >= MAX_TRIES) {
                unlockFails.put(key, new long[]{0, now + LOCK_SECONDS});
                log.warn("Staff Sell unlock locked for a minute after {} wrong tries (branch {})", MAX_TRIES, key);
            }
            // One message for every failure, so it cannot be used to find out who is a manager.
            return new Response<>("Wrong username or password, or this login cannot open POS");
        }
        unlockFails.remove(key);
        log.info("{} unlocked Staff Sell back to POS", user.getUsername());
        return new Response<>(Boolean.TRUE);
    }

    /**
     * What each staff member took on a day, by payment method, and what they
     * still hold unpaid. Bills opened on the Sales page are one more row with
     * no code, so the rows add up to the day's takings.
     */
    public Response<List<Map<String, Object>>> summary(LocalDate day) {
        String branchUID = LoggerUser.getBranchUIDOrMain();
        LocalDate d = day == null ? LocalDate.now() : day;
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();

        for (Object[] r : billPaymentRepository.takingsByStaffAndMethod(branchUID, d.atStartOfDay(), d.plusDays(1).atStartOfDay())) {
            Map<String, Object> row = row(rows, (String) r[0], (String) r[1]);
            @SuppressWarnings("unchecked")
            Map<String, Long> byMethod = (Map<String, Long>) row.get("byMethod");
            long amount = ((Number) r[3]).longValue();
            byMethod.merge(r[2] == null ? "other" : (String) r[2], amount, Long::sum);
            row.put("total", (Long) row.get("total") + amount);
            // A bill paid in two methods counts once per method here; keep the largest as a floor.
            row.put("paidBills", Math.max((Long) row.get("paidBills"), ((Number) r[4]).longValue()));
        }
        for (Object[] r : salesOpenedRepository.openBillsByStaff(branchUID)) {
            Map<String, Object> row = row(rows, (String) r[0], (String) r[1]);
            row.put("openBills", ((Number) r[2]).longValue());
            row.put("openAmount", ((Number) r[3]).longValue());
        }

        List<Map<String, Object>> out = new ArrayList<>(rows.values());
        // Staff by code, then the Sales page's own bills last.
        out.sort((a, b) -> {
            String ca = (String) a.get("staffCode"), cb = (String) b.get("staffCode");
            if (ca == null) return cb == null ? 0 : 1;
            if (cb == null) return -1;
            return ca.compareToIgnoreCase(cb);
        });
        return new Response<>(out);
    }

    private static Map<String, Object> row(Map<String, Map<String, Object>> rows, String code, String name) {
        return rows.computeIfAbsent(code == null ? "" : code, k -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("staffCode", code);
            row.put("staffName", name);
            row.put("total", 0L);
            row.put("paidBills", 0L);
            row.put("byMethod", new LinkedHashMap<String, Long>());
            row.put("openBills", 0L);
            row.put("openAmount", 0L);
            return row;
        });
    }
}
