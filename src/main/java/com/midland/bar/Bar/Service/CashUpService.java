package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Dto.CashUpDTO;
import com.midland.bar.Bar.Model.CashUp;
import com.midland.bar.Bar.Model.WorkShift;
import com.midland.bar.Bar.Model.CashUpLine;
import com.midland.bar.Bar.Repository.BillPaymentRepository;
import com.midland.bar.Bar.Repository.IncomeExpensesDescriptionRepository;
import com.midland.bar.Bar.Repository.CashUpLineRepository;
import com.midland.bar.Bar.Repository.CashUpRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cashing up a closed shift. What a cashier should have in hand is the
 * payments they took themselves (BillPayment.receivedBy) between opening and
 * closing that shift (see WorkShiftService). Payouts they recorded in the system
 * in that time (a pot's Pay, staff commission, stock purchase) come off the
 * expected of the method each went by - read from those records, never typed at the cash-up. They
 * count it per method; the cash-up keeps both and the difference, as submitted.
 */
@Service
@RequiredArgsConstructor
public class CashUpService {

    /** Roles that see every cashier's cash-ups; anyone else sees only their own. */
    private static final Set<String> SEES_ALL = Set.of("ROOT", "DIRECTOR", "CEO", "MANAGER");

    private final CashUpRepository cashUpRepository;
    private final CashUpLineRepository lineRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final IncomeExpensesDescriptionRepository payoutRepository;
    private final WorkShiftService workShiftService;
    private final com.midland.bar.Bar.Repository.StaffLossRepository staffLossRepository;

    /** The signed-in login's closed shift, waiting for its cash-up: when it ran, and what each method should hold. */
    public Response<Map<String, Object>> preview() {
        String branchUID = LoggerUser.getBranchUID();
        String email = LoggerUser.getEmail();
        Optional<WorkShift> closed = workShiftService.awaitingCashUp(branchUID, email);
        if (closed.isEmpty())
            return new Response<>("Close your shift before the cash-up");
        LocalDateTime from = closed.get().getOpenedAt();
        LocalDateTime to = closed.get().getClosedAt();
        Shift shift = shift(branchUID, email, from, to);

        List<Map<String, Object>> lines = new ArrayList<>();
        for (String method : shift.methods()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("method", method);
            line.put("takings", shift.takings(method));
            line.put("payouts", shift.payouts(method));
            line.put("expected", shift.expected(method));
            line.put("bills", shift.bills(method));
            lines.add(line);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cashierName", nameOf(LoggerUser.getUser()));
        body.put("from", from);
        body.put("to", to);
        body.put("lines", lines);
        body.put("payouts", shift.payoutRows);
        body.put("payoutsTotal", shift.payoutsTotal);
        body.put("expectedTotal", shift.expectedTotal());
        body.put("billCount", shift.billCount());
        return new Response<>(body);
    }

    /**
     * Closes the shift. Expected is worked out again here, at the moment of
     * closing, so it cannot be argued from the screen. Every method with
     * takings needs a count; cash may be counted even with none expected.
     */
    @Transactional
    public Response<CashUp> submit(CashUpDTO dto) {
        String branchUID = LoggerUser.getBranchUID();
        String email = LoggerUser.getEmail();
        if (branchUID == null || email == null)
            return new Response<>("Sign in again to close your shift");

        Map<String, Long> counted = new LinkedHashMap<>();
        if (dto != null && dto.getCounts() != null) {
            for (CashUpDTO.Count c : dto.getCounts()) {
                if (c.getMethod() == null || c.getCounted() == null)
                    continue;
                if (c.getCounted() < 0)
                    return new Response<>("A count cannot be below zero");
                counted.put(c.getMethod().trim().toLowerCase(), c.getCounted());
            }
        }

        Optional<WorkShift> closed = workShiftService.awaitingCashUp(branchUID, email);
        if (closed.isEmpty())
            return new Response<>("Close your shift before the cash-up");
        LocalDateTime from = closed.get().getOpenedAt();
        LocalDateTime to = closed.get().getClosedAt();
        Shift shift = shift(branchUID, email, from, to);

        for (String method : shift.methods()) {
            boolean moved = shift.takings(method) != 0 || shift.payouts(method) != 0;
            if (moved && !counted.containsKey(method))
                return new Response<>("Enter what you counted for " + method);
        }

        CashUp cashUp = new CashUp();
        cashUp.setCashierEmail(email);
        cashUp.setCashierName(nameOf(LoggerUser.getUser()));
        cashUp.setShiftUid(closed.get().getUid());
        cashUp.setPeriodFrom(from);
        cashUp.setPeriodTo(to);
        String note = dto == null || dto.getNote() == null ? null : dto.getNote().trim();
        cashUp.setNote(note == null || note.isEmpty() ? null : (note.length() > 500 ? note.substring(0, 500) : note));

        Set<String> methods = new TreeSet<>(shift.methods());
        methods.addAll(counted.keySet());
        long expectedTotal = 0, countedTotal = 0;
        int bills = 0;
        List<CashUpLine> lines = new ArrayList<>();
        for (String method : methods) {
            long exp = shift.expected(method);
            long cnt = counted.getOrDefault(method, 0L);
            CashUpLine line = new CashUpLine();
            line.setMethod(method);
            line.setTakings(shift.takings(method));
            line.setPayouts(shift.payouts(method));
            line.setExpected(exp);
            line.setCounted(cnt);
            line.setVariance(cnt - exp);
            line.setBills(shift.bills(method));
            lines.add(line);
            expectedTotal += exp;
            countedTotal += cnt;
            bills += shift.bills(method);
        }
        cashUp.setPayoutsTotal(shift.payoutsTotal);
        cashUp.setExpectedTotal(expectedTotal);
        cashUp.setCountedTotal(countedTotal);
        cashUp.setVariance(countedTotal - expectedTotal);
        cashUp.setBillCount(bills);
        CashUp saved = cashUpRepository.save(cashUp);
        for (CashUpLine line : lines) {
            line.setCashUp(saved);
            lineRepository.save(line);
        }
        workShiftService.markCashedUp(closed.get(), saved);
        return new Response<>(saved);
    }

    /** Cash-ups closed in a Reports period: all of them for managers, a cashier's own otherwise. */
    public ResponseList<CashUp> findClosed(String filter) {
        LocalDateTime[] range = ReportRange.of(filter);
        String email = seesAll() ? null : LoggerUser.getEmail();
        return new ResponseList<>(cashUpRepository.findClosed(LoggerUser.getBranchUID(), range[0], range[1], email));
    }

    public ResponseList<CashUpLine> findLines(String cashUpUid) {
        var cashUp = cashUpRepository.findInBranch(cashUpUid, LoggerUser.getBranchUID());
        if (cashUp.isEmpty() || (!seesAll() && !cashUp.get().getCashierEmail().equals(LoggerUser.getEmail())))
            return new ResponseList<>("Cash-up not found");
        return new ResponseList<>(lineRepository.findByCashUp(cashUpUid));
    }

    /** A shift's takings per method and the payouts recorded in it, each off the method it went by. */
    private Shift shift(String branchUID, String email, LocalDateTime from, LocalDateTime to) {
        Shift shift = new Shift();
        for (Object[] row : billPaymentRepository.takingsOf(branchUID, email, from, to)) {
            shift.takings.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
            shift.bills.put(String.valueOf(row[0]), ((Number) row[2]).intValue());
        }
        for (Object[] row : payoutRepository.payoutsOf(branchUID, email, from, to)) {
            long amount = row[3] == null ? 0 : ((Number) row[3]).longValue();
            Map<String, Object> payout = new LinkedHashMap<>();
            payout.put("pot", row[0]);
            payout.put("description", row[1]);
            payout.put("paidTo", row[2]);
            payout.put("amount", amount);
            payout.put("paidAt", row[4]);
            // Payouts recorded before the method was kept went out in cash.
            String method = row[5] == null || String.valueOf(row[5]).isBlank() ? "cash" : String.valueOf(row[5]);
            payout.put("method", method);
            shift.payoutRows.add(payout);
            shift.payoutsTotal += amount;
            shift.payouts.merge(method, amount, Long::sum);
        }
        // A staff member's handover shortage: their bills were paid in full,
        // the cash for them was short - so the cashier's drawer expects less.
        for (com.midland.bar.Bar.Model.StaffLoss loss : staffLossRepository.recordedBy(branchUID, email, from, to)) {
            long amount = loss.getAmount() == null ? 0 : loss.getAmount();
            Map<String, Object> payout = new LinkedHashMap<>();
            payout.put("pot", "Staff shortage");
            payout.put("description", loss.getNote() == null ? "" : loss.getNote());
            payout.put("paidTo", loss.getStaffName());
            payout.put("amount", amount);
            payout.put("paidAt", loss.getRecordedAt());
            payout.put("method", "cash");
            shift.payoutRows.add(payout);
            shift.payoutsTotal += amount;
            shift.payouts.merge("cash", amount, Long::sum);
        }
        return shift;
    }

    private static final class Shift {
        final Map<String, Long> takings = new LinkedHashMap<>();
        final Map<String, Integer> bills = new LinkedHashMap<>();
        final Map<String, Long> payouts = new LinkedHashMap<>();
        final List<Map<String, Object>> payoutRows = new ArrayList<>();
        long payoutsTotal = 0;

        Set<String> methods() {
            Set<String> m = new TreeSet<>(takings.keySet());
            m.addAll(payouts.keySet());
            return m;
        }

        long takings(String method) { return takings.getOrDefault(method, 0L); }
        long payouts(String method) { return payouts.getOrDefault(method, 0L); }
        long expected(String method) { return takings(method) - payouts(method); }
        int bills(String method) { return bills.getOrDefault(method, 0); }
        long expectedTotal() { return methods().stream().mapToLong(this::expected).sum(); }
        int billCount() { return bills.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    static boolean seesAll() {
        User user = LoggerUser.getUser();
        if (user == null)
            return false;
        if (Boolean.TRUE.equals(user.getIsRoot()))
            return true;
        return user.getRoles() != null && user.getRoles().stream().anyMatch(r -> SEES_ALL.contains(r.getCode()));
    }

    static String nameOf(User user) {
        if (user == null)
            return null;
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getUsername() : name;
    }
}
