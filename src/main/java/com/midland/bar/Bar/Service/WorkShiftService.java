package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Model.CashUp;
import com.midland.bar.Bar.Model.WorkShift;
import com.midland.bar.Bar.Repository.CashUpRepository;
import com.midland.bar.Bar.Repository.WorkShiftRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Utils.Exceptions.BusinessException;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Opening and closing a seller's shift. Whoever takes payments opens one
 * first; bills are opened, added to and paid only inside it. Closing it fixes
 * the window its cash-up counts, and the next one can't be opened until that
 * cash-up is done.
 */
@Service
@RequiredArgsConstructor
public class WorkShiftService {

    /** Roles above the till: their payouts are not drawer money, so they need no shift for those. */
    private static final Set<String> ABOVE_TILL = Set.of("ROOT", "DIRECTOR", "STAFF", "CEO", "MANAGER");

    private final WorkShiftRepository shiftRepository;
    private final CashUpRepository cashUpRepository;
    private final com.midland.bar.Utils.Offline.OfflineOps offlineOps;

    /**
     * The signed-in login's shift: NONE, OPEN, or CLOSED (waiting for its
     * cash-up). The CEO and manager also get `others`: the shifts other
     * people have open in the branch right now, so they can see the till is
     * running. That is for seeing only - to sell they still open their own.
     */
    public Response<Map<String, Object>> current() {
        String branchUID = LoggerUser.getBranchUID();
        String email = LoggerUser.getEmail();
        Map<String, Object> body = new LinkedHashMap<>();
        // A staff member signed in with their code has no shift of their own:
        // they sell on the branch's - any cashier's open shift.
        Optional<WorkShift> shift = com.midland.bar.Config.Security.StaffSession.active()
                ? branchOpenShift(branchUID)
                : unfinished(branchUID, email);
        body.put("state", shift.map(WorkShift::getStatus).orElse("NONE"));
        body.put("shift", shift.orElse(null));
        List<Map<String, Object>> others = new ArrayList<>();
        if (branchUID != null && CashUpService.seesAll()) {
            for (WorkShift s : shiftRepository.findAllUnfinished(branchUID, null)) {
                if (!WorkShift.OPEN.equals(s.getStatus()) || s.getCashierEmail().equals(email))
                    continue;
                Map<String, Object> other = new LinkedHashMap<>();
                other.put("cashierName", s.getCashierName() != null ? s.getCashierName() : s.getCashierEmail());
                other.put("openedAt", s.getOpenedAt());
                others.add(other);
            }
        }
        body.put("others", others);
        return new Response<>(body);
    }

    @Transactional
    public Response<WorkShift> open() {
        String branchUID = LoggerUser.getBranchUID();
        String email = LoggerUser.getEmail();
        if (branchUID == null || email == null)
            return new Response<>("Sign in again to open your shift");
        Optional<String> done = offlineOps.alreadyApplied();
        if (done.isPresent())
            return shiftRepository.findById(done.get()).map(Response::new).orElseGet(() -> new Response<>("Shift not found"));
        Optional<WorkShift> existing = unfinished(branchUID, email);
        if (existing.isPresent()) {
            return WorkShift.OPEN.equals(existing.get().getStatus())
                    ? new Response<>("Your shift is already open")
                    : new Response<>("Do the cash-up of your closed shift before opening a new one");
        }
        WorkShift shift = new WorkShift();
        shift.setCashierEmail(email);
        shift.setCashierName(CashUpService.nameOf(LoggerUser.getUser()));
        // A shift opened offline carries the device's time, which may be earlier
        // than this login's last shift ended (a queue held back while that one
        // was cashed up). Never before it: two shifts must not share a payment.
        LocalDateTime openedAt = com.midland.bar.Utils.Offline.OfflineContext.now();
        LocalDateTime lastEnd = shiftRepository.lastClosedAt(branchUID, email).orElse(null);
        if (lastEnd != null && openedAt.isBefore(lastEnd))
            openedAt = lastEnd;
        shift.setOpenedAt(openedAt);
        shift.setStatus(WorkShift.OPEN);
        WorkShift saved = shiftRepository.save(shift);
        offlineOps.claim("SHIFT_OPEN", saved.getUid());
        return new Response<>(saved);
    }

    @Transactional
    public Response<WorkShift> close() {
        Optional<String> done = offlineOps.alreadyApplied();
        if (done.isPresent())
            return shiftRepository.findById(done.get()).map(Response::new).orElseGet(() -> new Response<>("Shift not found"));
        Optional<WorkShift> shift = unfinished(LoggerUser.getBranchUID(), LoggerUser.getEmail())
                .filter(s -> WorkShift.OPEN.equals(s.getStatus()));
        if (shift.isEmpty())
            return new Response<>("You have no open shift to close");
        WorkShift s = shift.get();
        s.setClosedAt(com.midland.bar.Utils.Offline.OfflineContext.now());
        s.setStatus(WorkShift.CLOSED);
        s.update();
        WorkShift saved = shiftRepository.save(s);
        offlineOps.claim("SHIFT_CLOSE", saved.getUid());
        return new Response<>(saved);
    }

    /** Selling - opening a bill, adding to it, taking payment - needs the login's shift open. */
    public void requireOpen() {
        if (com.midland.bar.Config.Security.StaffSession.active()) {
            if (branchOpenShift(LoggerUser.getBranchUID()).isEmpty())
                throw new BusinessException("No shift is open - wait for the cashier to open one");
            return;
        }
        Optional<WorkShift> shift = unfinished(LoggerUser.getBranchUID(), LoggerUser.getEmail());
        if (shift.isEmpty())
            throw new BusinessException("Open your shift before selling");
        if (!WorkShift.OPEN.equals(shift.get().getStatus()))
            throw new BusinessException("Your shift is closed - do its cash-up and open a new shift before selling");
    }

    /**
     * Paying out of the drawer (a pot, a commission, a stock purchase) is a
     * cashier's job and comes off their cash-up, so it has to fall inside
     * their shift. CEO and above pay from wherever the money is; they need none.
     */
    public void requireOpenForPayout() {
        User user = LoggerUser.getUser();
        boolean aboveTill = user != null && (Boolean.TRUE.equals(user.getIsRoot())
                || (user.getRoles() != null && user.getRoles().stream().map(Role::getCode).anyMatch(ABOVE_TILL::contains)));
        if (!aboveTill)
            requireOpen();
    }

    /** The closed shift waiting for this login's cash-up, if there is one. */
    public Optional<WorkShift> awaitingCashUp(String branchUID, String email) {
        return unfinished(branchUID, email).filter(s -> WorkShift.CLOSED.equals(s.getStatus()));
    }

    void markCashedUp(WorkShift shift, CashUp cashUp) {
        shift.setStatus(WorkShift.CASHED_UP);
        shift.setCashUpUid(cashUp.getUid());
        shift.update();
        shiftRepository.save(shift);
    }

    /**
     * Shifts for the CEO and manager to look over: those opened in the period
     * plus any still open or waiting for cash-up. A cashier sees their own.
     */
    public ResponseList<Map<String, Object>> list(String filter) {
        String branchUID = LoggerUser.getBranchUID();
        String email = CashUpService.seesAll() ? null : LoggerUser.getEmail();
        LocalDateTime[] range = ReportRange.of(filter);
        Map<String, WorkShift> shifts = new LinkedHashMap<>();
        for (WorkShift s : shiftRepository.findAllUnfinished(branchUID, email))
            shifts.put(s.getUid(), s);
        for (WorkShift s : shiftRepository.findOpenedIn(branchUID, range[0], range[1], email))
            shifts.putIfAbsent(s.getUid(), s);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WorkShift s : shifts.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uid", s.getUid());
            row.put("cashierName", s.getCashierName());
            row.put("cashierEmail", s.getCashierEmail());
            row.put("openedAt", s.getOpenedAt());
            row.put("closedAt", s.getClosedAt());
            row.put("status", s.getStatus());
            LocalDateTime end = s.getClosedAt() != null ? s.getClosedAt() : LocalDateTime.now();
            row.put("minutes", Duration.between(s.getOpenedAt(), end).toMinutes());
            CashUp cashUp = s.getCashUpUid() == null ? null
                    : cashUpRepository.findInBranch(s.getCashUpUid(), branchUID).orElse(null);
            row.put("cashUpUid", s.getCashUpUid());
            row.put("expectedTotal", cashUp == null ? null : cashUp.getExpectedTotal());
            row.put("countedTotal", cashUp == null ? null : cashUp.getCountedTotal());
            row.put("variance", cashUp == null ? null : cashUp.getVariance());
            rows.add(row);
        }
        return new ResponseList<>(rows);
    }

    /** Any cashier's OPEN shift in the branch - what a staff code session sells on. */
    private Optional<WorkShift> branchOpenShift(String branchUID) {
        if (branchUID == null)
            return Optional.empty();
        return shiftRepository.findAllUnfinished(branchUID, null).stream()
                .filter(s -> WorkShift.OPEN.equals(s.getStatus()))
                .findFirst();
    }

    private Optional<WorkShift> unfinished(String branchUID, String email) {
        if (branchUID == null || email == null)
            return Optional.empty();
        return shiftRepository.findUnfinished(branchUID, email).stream().findFirst();
    }
}
