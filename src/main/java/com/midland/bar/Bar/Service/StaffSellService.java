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

    /** Roles whose login may take the screen back from Staff Sell to POS. */
    private static final Set<String> UNLOCK_ROLES = Set.of("ROOT", "STAFF", "DIRECTOR", "CEO", "MANAGER");
    private static final int MAX_TRIES = 5;
    private static final long LOCK_SECONDS = 60;

    /** Wrong unlock attempts per branch: [count, locked-until epoch second]. Memory only - a restart clears it. */
    private final Map<String, long[]> unlockFails = new ConcurrentHashMap<>();

    /** Who the code belongs to, and their unpaid bills. */
    public Response<Map<String, Object>> findByCode(String code) {
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

    /** Hand the staff member's written orders to the supervisor - on switching staff, or Send. */
    public Response<Integer> sendOrders(String staffCode) {
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
