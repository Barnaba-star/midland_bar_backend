package com.midland.bar.Payment.Service;

import com.midland.bar.Payment.Dto.ShareRecipientDTO;
import com.midland.bar.Payment.Dto.StaffCommissionDTO;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Utils.Responses.ResponseList;
import java.util.ArrayList;
import java.util.List;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Payment.Model.CommissionPayout;
import com.midland.bar.Payment.Repository.CommissionPayoutRepository;
import org.springframework.transaction.annotation.Transactional;
import com.midland.bar.Payment.Dto.RevenueShareDTO;
import com.midland.bar.Payment.Projection.PaymentTotalsProjection;
import com.midland.bar.Payment.Repository.SubscriptionPaymentRepository;
import com.midland.bar.Setting.Model.PlatformSetting;
import com.midland.bar.Setting.Service.PlatformSettingService;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Splits a month's subscription income between staff, directors, root and
 * what is left to run on.
 *
 * The director count is read rather than fixed: adding a second director
 * doubles that share on its own, with nothing to remember to change.
 */
@Service
@RequiredArgsConstructor
public class RevenueShareService {

    private static final String DIRECTOR_ROLE_CODE = "DIRECTOR";
    private static final String ROOT_ROLE_CODE = "ROOT";
    private static final String STAFF_ROLE_CODE = "STAFF";

    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final PlatformSettingService platformSettingService;
    private final UserRepository userRepository;
    private final CommissionService commissionService;
    private final CommissionPayoutRepository commissionPayoutRepository;

    public Response<RevenueShareDTO> findRevenueShare(Integer year, Integer month) {
        YearMonth period = (year == null || month == null) ? YearMonth.now() : YearMonth.of(year, month);
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();

        PaymentTotalsProjection totals = subscriptionPaymentRepository.totalsBetween(start, end);
        long revenue = totals == null || totals.getTotalAmount() == null ? 0 : totals.getTotalAmount();
        long staffAmount = totals == null || totals.getTotalCommission() == null ? 0 : totals.getTotalCommission();

        PlatformSetting setting = platformSettingService.current();
        int directorCount = userRepository.findAllByRoleCode(DIRECTOR_ROLE_CODE).size();

        RevenueShareDTO share = new RevenueShareDTO();
        share.setYear(period.getYear());
        share.setMonth(period.getMonthValue());
        share.setRevenue(revenue);
        share.setPayments(totals == null || totals.getPayments() == null ? 0 : totals.getPayments());

        share.setStaffPercent(orZero(setting.getCommissionPercent()));
        share.setStaffAmount(staffAmount);

        // A pool for the role, divided among whoever holds it - not a
        // percentage each. Multiplying by the count made the company's
        // obligation grow with every director appointed: three directors at
        // 10% each took 30% of the month, and nothing capped it. Directors
        // and root work the same way; only staff earn individually, and they
        // earn it per branch they registered themselves.
        share.setDirectorPercent(orZero(setting.getDirectorPercent()));
        share.setDirectorCount(directorCount);
        share.setDirectorAmount(percentOf(revenue, share.getDirectorPercent()));

        share.setRootPercent(orZero(setting.getRootPercent()));
        share.setRootCount(userRepository.findAllByRoleCode(ROOT_ROLE_CODE).size());
        share.setRootAmount(percentOf(revenue, share.getRootPercent()));

        // Clamped at zero: percentages that add past 100 would otherwise show
        // a negative running budget, which reads as a debt rather than as the
        // misconfiguration it is.
        long shared = share.getStaffAmount() + share.getDirectorAmount() + share.getRootAmount();
        share.setOperatingAmount(Math.max(0, revenue - shared));

        return new Response<>(share);
    }

    private static YearMonth periodOf(Integer year, Integer month) {
        return (year == null || month == null) ? YearMonth.now() : YearMonth.of(year, month);
    }

    private static long percentOf(long amount, long percent) {
        return Math.round(amount * percent / 100.0);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Who is owed the share a row on the summary stands for.
     *
     * Staff come from the commission report rather than a fresh calculation:
     * that report is what they are actually paid from, and two ways of
     * working out the same figure would eventually disagree in front of
     * someone expecting money.
     */
    public ResponseList<ShareRecipientDTO> findShareRecipients(String role, Integer year, Integer month) {
        String wanted = role == null ? "" : role.trim().toUpperCase();

        if (STAFF_ROLE_CODE.equals(wanted)) {
            List<ShareRecipientDTO> staff = new ArrayList<>();
            for (StaffCommissionDTO row : commissionService.staffCommissionReport(year, month).getData()) {
                staff.add(new ShareRecipientDTO(
                        row.getStaffUid(),
                        row.getStaffName(),
                        row.getUsername(),
                        row.getCommissionDue(),
                        row.getCommissionPaid(),
                        row.getOutstanding()
                ));
            }
            return new ResponseList<>(staff);
        }

        RevenueShareDTO share = findRevenueShare(year, month).getData();

        if (DIRECTOR_ROLE_CODE.equals(wanted)) {
            // Each director is due the same percentage, so the pool divides
            // evenly by definition rather than by choice.
            long each = share.getDirectorCount() == 0
                    ? 0
                    : share.getDirectorAmount() / share.getDirectorCount();
            return new ResponseList<>(toRecipients(DIRECTOR_ROLE_CODE, each, periodOf(year, month)));
        }

        if (ROOT_ROLE_CODE.equals(wanted)) {
            List<User> roots = userRepository.findAllByRoleCode(ROOT_ROLE_CODE);
            // ROOT's share is one share for the role, not one each - split it
            // rather than paying it out once per holder.
            long each = roots.isEmpty() ? 0 : share.getRootAmount() / roots.size();
            return new ResponseList<>(toRecipients(ROOT_ROLE_CODE, each, periodOf(year, month)));
        }

        return new ResponseList<>("Unknown share");
    }

    private List<ShareRecipientDTO> toRecipients(String roleCode, long each, YearMonth period) {
        List<ShareRecipientDTO> recipients = new ArrayList<>();
        for (User user : userRepository.findAllByRoleCode(roleCode)) {
            long paid = commissionPayoutRepository.paidForShare(
                    user.getUid(), roleCode, period.getYear(), period.getMonthValue());
            recipients.add(new ShareRecipientDTO(
                    user.getUid(),
                    displayName(user),
                    user.getUsername(),
                    each,
                    paid,
                    // Clamped: a share lowered after someone was paid should
                    // read as nothing owed, not as money to claw back.
                    Math.max(0, each - paid)
            ));
        }
        return recipients;
    }

    private static String displayName(User user) {
        String name = String.format("%s %s",
                user.getFirstName() == null ? "" : user.getFirstName(),
                user.getLastName() == null ? "" : user.getLastName()).trim();
        return name.isBlank() ? user.getUsername() : name;
    }

    /**
     * Records a payout against one person's share.
     *
     * Staff are handed straight to the commission service rather than paid a
     * second way from here - one route to staff money, not two that could
     * disagree about what is still owed.
     */
    @Transactional
    /**
     * Records a payout against one person's share.
     *
     * Returns a code rather than a sentence: the wording a person reads
     * belongs to the screen, which has it translated. The amount actually
     * recorded comes back on success so the caller can update in place
     * instead of refetching.
     */
    public Response<Integer> payShare(String role, String uid, Integer year, Integer month, String note, Integer amount) {
        String wanted = role == null ? "" : role.trim().toUpperCase();
        if (uid == null || uid.isBlank()) {
            return new Response<>("NO_RECIPIENT");
        }

        if (STAFF_ROLE_CODE.equals(wanted)) {
            Response<CommissionPayout> paid = commissionService.payStaffCommission(uid, year, month, note, amount);
            return paid.getData() != null
                    ? new Response<>(paid.getData().getAmount())
                    : new Response<>(codeFor(paid.getMessage()));
        }

        if (!DIRECTOR_ROLE_CODE.equals(wanted) && !ROOT_ROLE_CODE.equals(wanted)) {
            return new Response<>("UNKNOWN_SHARE");
        }

        // ROOT and DIRECTOR both record these. A director settling their own
        // share is a conflict on paper, but the audit log names whoever
        // pressed the button - the trail is the control here, not the lock.
        User payer = LoggerUser.getUser();
        boolean mayPay = Boolean.TRUE.equals(payer.getIsRoot())
                || (payer.getRoles() != null && payer.getRoles().stream()
                        .anyMatch(r -> ROOT_ROLE_CODE.equals(r.getCode()) || DIRECTOR_ROLE_CODE.equals(r.getCode())));
        if (!mayPay) {
            return new Response<>("NOT_ALLOWED");
        }

        YearMonth period = (year == null || month == null) ? YearMonth.now() : YearMonth.of(year, month);

        ShareRecipientDTO recipient = findShareRecipients(wanted, period.getYear(), period.getMonthValue())
                .getData().stream()
                .filter(r -> uid.equals(r.getUid()))
                .findFirst()
                .orElse(null);

        if (recipient == null) {
            return new Response<>("NOT_IN_ROLE");
        }
        if (recipient.getOutstanding() <= 0) {
            return new Response<>(recipient.getAmount() <= 0 ? "NOTHING_EARNED" : "ALREADY_PAID");
        }

        long paying = amount == null ? recipient.getOutstanding() : amount;
        if (paying <= 0) {
            return new Response<>("INVALID_AMOUNT");
        }
        if (paying > recipient.getOutstanding()) {
            return new Response<>("MORE_THAN_OWED");
        }

        CommissionPayout payout = new CommissionPayout();
        payout.setRoleCode(wanted);
        payout.setStaffUid(uid);
        payout.setStaffName(recipient.getName());
        payout.setPeriodYear(period.getYear());
        payout.setPeriodMonth(period.getMonthValue());
        payout.setAmount((int) paying);
        payout.setPaidAt(LocalDate.now());
        payout.setNote(note);
        payout.setPaidByUid(payer.getUid());
        payout.setPaidByName(displayName(payer));

        commissionPayoutRepository.save(payout);
        return new Response<>((int) paying);
    }

    /**
     * The commission service still answers in sentences, since its own screen
     * shows them. Mapped here so this endpoint speaks only in codes.
     */
    private static String codeFor(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("already been paid")) return "ALREADY_PAID";
        if (text.contains("earned no commission")) return "NOTHING_EARNED";
        if (text.contains("more than is owed")) return "MORE_THAN_OWED";
        if (text.contains("greater than zero")) return "INVALID_AMOUNT";
        if (text.contains("not allowed")) return "NOT_ALLOWED";
        if (text.contains("not found")) return "NOT_IN_ROLE";
        return "PAY_FAILED";
    }

}
