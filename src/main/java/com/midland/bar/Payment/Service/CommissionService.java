package com.midland.bar.Payment.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Payment.Dto.StaffBranchDTO;
import com.midland.bar.Payment.Dto.StaffCommissionDTO;
import com.midland.bar.Payment.Model.CommissionPayout;
import com.midland.bar.Payment.Model.SubscriptionPayment;
import com.midland.bar.Payment.Projection.BranchCountProjection;
import com.midland.bar.Payment.Projection.PayoutTotalProjection;
import com.midland.bar.Payment.Projection.StaffEarningProjection;
import com.midland.bar.Payment.Repository.CommissionPayoutRepository;
import com.midland.bar.Payment.Repository.SubscriptionPaymentRepository;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Setting.Service.PlatformSettingService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Staff bring in branches and earn a percentage of whatever those branches
 * pay, for as long as they keep paying. Everything here reads from
 * subscription_payments, never from the branches table - branches only ever
 * hold the CURRENT subscription state, which the next payment overwrites.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final BranchRepository branchRepository;
    private final CommissionPayoutRepository commissionPayoutRepository;
    private final UserRepository userRepository;
    private final PlatformSettingService platformSettingService;

    private static final String STAFF_ROLE_CODE = "STAFF";

    /**
     * Called from the Snippe webhook once a payment has actually completed.
     * Returns quietly if this reference was already recorded - Snippe
     * redelivers events, and a double row here would pay a staff member twice.
     */
    public void recordPayment(Branch branch, String reference, Integer amount, int months) {

        if (reference != null && subscriptionPaymentRepository.existsByReference(reference)) {
            log.info("Subscription payment {} already recorded, skipping", reference);
            return;
        }

        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setReference(reference);
        payment.setBranchUid(branch.getUid());
        payment.setBranchName(branch.getBranchName());
        payment.setBranchCode(branch.getBranchCode());
        payment.setMonths(months);
        payment.setPaidAt(LocalDate.now());

        // Fall back to the branch's configured rate if the webhook didn't
        // carry an amount - better a computed figure than a null in a
        // report someone is about to pay real money against.
        int paid = amount != null && amount > 0
                ? amount
                : (branch.getSubscriptionAmount() != null ? branch.getSubscriptionAmount() * months : 0);
        payment.setAmount(paid);

        // Read per payment, not once at startup: the rate is snapshot onto
        // this row, so a change in Settings applies from the next payment on
        // and never moves money that was already recorded.
        int commissionPercent = platformSettingService.current().getCommissionPercent();
        payment.setCommissionPercent(commissionPercent);
        payment.setCommissionAmount(Math.round(paid * commissionPercent / 100f));

        String staffUid = branch.getCreatedBy();
        payment.setStaffUid(staffUid);
        if (staffUid != null) {
            userRepository.findById(staffUid)
                    .ifPresent(staff -> payment.setStaffName(fullName(staff)));
        }

        try {
            subscriptionPaymentRepository.save(payment);
            log.info("Recorded subscription payment {} for branch {} ({} TZS, commission {})",
                    reference, branch.getBranchName(), paid, payment.getCommissionAmount());
        } catch (Exception e) {
            // Never let bookkeeping break the subscription itself - the
            // branch has paid and must be extended either way.
            log.error("Failed to record subscription payment {}: {}", reference, e.getMessage());
        }
    }

    /**
     * The commission report for one month. Every staff member who has ever
     * registered a branch appears, including those who earned nothing this
     * month - a zero row is the useful signal that their branches stopped
     * paying, and silently dropping them would hide it.
     */
    public ResponseList<StaffCommissionDTO> staffCommissionReport(Integer year, Integer month) {
        return staffCommissionReport(year, month, null);
    }

    public ResponseList<StaffCommissionDTO> staffCommissionReport(Integer year, Integer month, String search) {

        YearMonth period = (year == null || month == null)
                ? YearMonth.now()
                : YearMonth.of(year, month);

        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();

        log.info("{} is accessing the commission report for {}", LoggerUser.getEmail(), period);

        Map<String, StaffCommissionDTO> rows = new LinkedHashMap<>();

        // Start from everyone who HOLDS the STAFF role, not from whoever
        // happens to have registered a branch. A new staff member belongs on
        // this report from day one, showing zeros - that is how you see they
        // have brought nothing in yet.
        for (User staff : userRepository.findAllByRoleCode(STAFF_ROLE_CODE)) {
            StaffCommissionDTO dto = new StaffCommissionDTO();
            dto.setStaffUid(staff.getUid());
            dto.setStaffName(fullName(staff));
            dto.setUsername(staff.getUsername());
            rows.put(staff.getUid(), dto);
        }

        // Anyone else who registered branches still gets a row - a DIRECTOR or
        // ROOT can register one too, and dropping them would make the rows
        // stop adding up to the month's total.
        for (BranchCountProjection count : branchRepository.countBranchesByCreator()) {
            StaffCommissionDTO dto = rows.computeIfAbsent(count.getStaffUid(), uid -> {
                StaffCommissionDTO extra = new StaffCommissionDTO();
                extra.setStaffUid(uid);
                return extra;
            });
            dto.setBranchesRegistered(count.getBranchesRegistered() == null ? 0 : count.getBranchesRegistered());
        }

        for (StaffEarningProjection earning : subscriptionPaymentRepository.earningsBetween(start, end)) {
            StaffCommissionDTO dto = rows.computeIfAbsent(earning.getStaffUid(), uid -> {
                // Earned this month but registers no live branch any more -
                // the branch was deleted. They are still owed the money.
                StaffCommissionDTO fresh = new StaffCommissionDTO();
                fresh.setStaffUid(uid);
                return fresh;
            });
            dto.setStaffName(earning.getStaffName());
            dto.setBranchesPaid(orZero(earning.getBranchesPaid()));
            dto.setPayments(orZero(earning.getPayments()));
            dto.setTotalCollected(orZero(earning.getTotalCollected()));
            dto.setCommissionDue(orZero(earning.getCommissionDue()));
        }

        // What has already been handed over for this month.
        for (PayoutTotalProjection payout : commissionPayoutRepository.totalsForPeriod(period.getYear(), period.getMonthValue())) {
            StaffCommissionDTO dto = rows.get(payout.getStaffUid());
            if (dto == null) {
                continue;
            }
            dto.setCommissionPaid(orZero(payout.getAmountPaid()));
            dto.setLastPaidAt(payout.getLastPaidAt());
        }

        // Outstanding is clamped at zero: if a rate was lowered after a payout,
        // or someone was overpaid by hand, the report should read "nothing
        // owed" rather than a negative figure that looks like a debt.
        for (StaffCommissionDTO dto : rows.values()) {
            dto.setOutstanding(Math.max(0, dto.getCommissionDue() - dto.getCommissionPaid()));
        }

        // Names: the snapshot on the payment is authoritative, but staff with
        // no payment this month have none, so look those up.
        Map<String, User> staffByUid = userRepository.findAllById(rows.keySet()).stream()
                .collect(Collectors.toMap(User::getUid, u -> u));
        for (StaffCommissionDTO dto : rows.values()) {
            User user = staffByUid.get(dto.getStaffUid());
            if (user == null) {
                continue;
            }
            dto.setUsername(user.getUsername());
            if (dto.getStaffName() == null) {
                dto.setStaffName(fullName(user));
            }
        }

        List<StaffCommissionDTO> report = new ArrayList<>(rows.values());

        // Applied here rather than in a query: the rows are an aggregate of
        // four different sources, and the name is only final by this point.
        String term = search == null || search.isBlank() ? null : search.trim().toLowerCase();
        if (term != null) {
            report.removeIf(r -> !matches(r, term));
        }

        // STAFF see only their own earnings - never a colleague's.
        if (!seesAllStaff()) {
            String self = LoggerUser.getUser().getUid();
            report.removeIf(r -> !self.equals(r.getStaffUid()));
        }
        report.sort(Comparator.comparingLong(StaffCommissionDTO::getCommissionDue).reversed()
                .thenComparing(StaffCommissionDTO::getStaffName, Comparator.nullsLast(String::compareTo)));

        return new ResponseList<>(report);
    }

    private static boolean matches(StaffCommissionDTO row, String term) {
        return contains(row.getStaffName(), term) || contains(row.getUsername(), term);
    }

    private static boolean contains(String value, String term) {
        return value != null && value.toLowerCase().contains(term);
    }

    /**
     * The individual payments behind one staff member's figure, so a total
     * can be checked branch by branch before money changes hands.
     */
    public ResponseList<SubscriptionPayment> staffPayments(String staffUid, Integer year, Integer month) {

        if (staffUid == null)
            return new ResponseList<>("Provide staff ref UID");

        if (!mayViewStaff(staffUid))
            return new ResponseList<>("Not Allowed To View Another Staff Member");

        YearMonth period = (year == null || month == null)
                ? YearMonth.now()
                : YearMonth.of(year, month);

        return new ResponseList<>(subscriptionPaymentRepository.findStaffPayments(
                staffUid, period.atDay(1), period.atEndOfMonth()));
    }

    /**
     * Hand a staff member what they are still owed for a month.
     *
     * The amount is ALWAYS recomputed here and never taken from the caller -
     * a client-supplied figure is a client-supplied bank transfer. Paying
     * twice is prevented by the same recomputation: once a payout covers the
     * month, outstanding is zero and this refuses.
     */
    public Response<CommissionPayout> payStaffCommission(String staffUid, Integer year, Integer month, String note) {
        return payStaffCommission(staffUid, year, month, note, null);
    }

    /**
     * @param amount how much is being handed over, or null for everything
     *               still owed. A part payment leaves the rest outstanding
     *               rather than closing the month off.
     */
    public Response<CommissionPayout> payStaffCommission(String staffUid, Integer year, Integer month, String note, Integer amount) {

        if (staffUid == null)
            return new Response<>("Provide staff ref UID");

        // Belt and braces on top of PAY_COMMISSION: staffCommissionReport
        // filters to the caller's own row for a STAFF member, so without this
        // anyone who was ever granted that permission could pay themselves.
        if (!seesAllStaff())
            return new Response<>("Not Allowed To Pay Commission");

        YearMonth period = (year == null || month == null)
                ? YearMonth.now()
                : YearMonth.of(year, month);

        StaffCommissionDTO row = staffCommissionReport(period.getYear(), period.getMonthValue())
                .getData().stream()
                .filter(r -> staffUid.equals(r.getStaffUid()))
                .findFirst()
                .orElse(null);

        if (row == null)
            return new Response<>("Staff Not Found In This Period");

        if (row.getOutstanding() <= 0) {
            return new Response<>(row.getCommissionDue() <= 0
                    ? "This Staff Earned No Commission In This Period"
                    : "This Commission Has Already Been Paid");
        }

        long paying = amount == null ? row.getOutstanding() : amount;
        if (paying <= 0) {
            return new Response<>("Provide An Amount Greater Than Zero");
        }
        if (paying > row.getOutstanding()) {
            return new Response<>("That Is More Than Is Owed For This Period");
        }

        CommissionPayout payout = new CommissionPayout();
        payout.setStaffUid(staffUid);
        payout.setStaffName(row.getStaffName());
        payout.setPeriodYear(period.getYear());
        payout.setPeriodMonth(period.getMonthValue());
        payout.setAmount((int) paying);
        payout.setPaidAt(LocalDate.now());
        payout.setNote(note);

        User payer = LoggerUser.getUser();
        payout.setPaidByUid(payer.getUid());
        payout.setPaidByName(fullName(payer));

        try {
            CommissionPayout saved = commissionPayoutRepository.save(payout);
            log.info("{} paid {} commission of {} for {}",
                    payer.getUsername(), row.getStaffName(), payout.getAmount(), period);
            return new Response<>(saved);
        } catch (Exception e) {
            log.error("Failed to record commission payout: {}", e.getMessage());
            return new Response<>("Failed To Record The Payout");
        }
    }

    /**
     * The payouts already made to one staff member for a month - a month can
     * hold more than one when a top-up was needed.
     */
    public ResponseList<CommissionPayout> staffPayouts(String staffUid, Integer year, Integer month) {

        if (staffUid == null)
            return new ResponseList<>("Provide staff ref UID");

        if (!mayViewStaff(staffUid))
            return new ResponseList<>("Not Allowed To View Another Staff Member");

        YearMonth period = (year == null || month == null)
                ? YearMonth.now()
                : YearMonth.of(year, month);

        return new ResponseList<>(commissionPayoutRepository.findForStaffPeriod(
                staffUid, period.getYear(), period.getMonthValue()));
    }

    /**
     * Every branch a staff member registered, each marked paid or not for the
     * month in question.
     *
     * Built from the branch list first and the payments second, never the
     * other way round: a report driven by payments can only show branches
     * that paid, and the ones that went quiet are exactly what this is for.
     */
    public ResponseList<StaffBranchDTO> staffBranches(String staffUid, Integer year, Integer month) {

        if (staffUid == null)
            return new ResponseList<>("Provide staff ref UID");

        if (!mayViewStaff(staffUid))
            return new ResponseList<>("Not Allowed To View Another Staff Member");

        YearMonth period = (year == null || month == null)
                ? YearMonth.now()
                : YearMonth.of(year, month);

        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();

        Map<String, StaffBranchDTO> rows = new LinkedHashMap<>();

        for (Branch branch : branchRepository.findAllByCreator(staffUid)) {
            StaffBranchDTO dto = new StaffBranchDTO();
            dto.setBranchUid(branch.getUid());
            dto.setBranchName(branch.getBranchName());
            dto.setBranchCode(branch.getBranchCode());
            dto.setRegion(branch.getRegion());
            dto.setRegisteredAt(branch.getCreatedAt());
            dto.setSubscriptionStatus(branch.getSubscriptionStatus());
            dto.setCloseSubscription(branch.getCloseSubscription());
            rows.put(branch.getUid(), dto);
        }

        for (SubscriptionPayment payment : subscriptionPaymentRepository.findStaffPayments(staffUid, start, end)) {

            StaffBranchDTO dto = rows.computeIfAbsent(payment.getBranchUid(), uid -> {
                // Paid this month but no longer in the branch list: the branch
                // was deleted. The commission still counts, so show it rather
                // than let the rows silently stop adding up to the total.
                StaffBranchDTO gone = new StaffBranchDTO();
                gone.setBranchUid(uid);
                gone.setBranchName(payment.getBranchName());
                gone.setBranchCode(payment.getBranchCode());
                gone.setDeleted(true);
                return gone;
            });

            dto.setPaid(true);
            dto.setPayments(dto.getPayments() + 1);
            dto.setAmountPaid(dto.getAmountPaid() + (payment.getAmount() == null ? 0 : payment.getAmount()));
            dto.setCommission(dto.getCommission() + (payment.getCommissionAmount() == null ? 0 : payment.getCommissionAmount()));

            if (dto.getLastPaidAt() == null || payment.getPaidAt().isAfter(dto.getLastPaidAt())) {
                dto.setLastPaidAt(payment.getPaidAt());
            }
        }

        // Unpaid first - those are the ones that need chasing.
        List<StaffBranchDTO> branches = new ArrayList<>(rows.values());
        branches.sort(Comparator.comparing(StaffBranchDTO::isPaid)
                .thenComparing(StaffBranchDTO::getBranchName, Comparator.nullsLast(String::compareTo)));

        return new ResponseList<>(branches);
    }

    /**
     * ROOT and DIRECTOR see every staff member; a STAFF member only ever sees
     * themselves. Mirrors the same split in RoleService/UserService.
     */
    private boolean seesAllStaff() {
        User user = LoggerUser.getUser();
        List<String> roleCodes = user.getRoles() == null
                ? List.of()
                : user.getRoles().stream().map(Role::getCode).toList();
        return Boolean.TRUE.equals(user.getIsRoot())
                || roleCodes.contains("ROOT")
                || roleCodes.contains("DIRECTOR");
    }

    /**
     * Hiding other people's rows in the UI is not enough - the uid is a path
     * parameter, so a STAFF member could simply ask for a colleague's. Every
     * per-staff endpoint goes through here first.
     */
    private boolean mayViewStaff(String staffUid) {
        return seesAllStaff() || LoggerUser.getUser().getUid().equals(staffUid);
    }

    private long orZero(Long value) {
        return value == null ? 0 : value;
    }

    private String fullName(User user) {
        return String.join(" ",
                        user.getFirstName() == null ? "" : user.getFirstName(),
                        user.getMiddleName() == null ? "" : user.getMiddleName(),
                        user.getLastName() == null ? "" : user.getLastName())
                .replaceAll("\\s+", " ")
                .trim();
    }
}
