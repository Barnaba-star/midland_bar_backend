package com.midland.bar.Payment.Controller;

import com.midland.bar.Payment.Model.SubscriptionPayment;
import com.midland.bar.Payment.Projection.PaymentTotalsProjection;
import com.midland.bar.Payment.Repository.SubscriptionPaymentRepository;
import com.midland.bar.Payment.Dto.PayrollDTO;
import com.midland.bar.Payment.Dto.RevenueShareDTO;
import com.midland.bar.Payment.Dto.ShareRecipientDTO;
import com.midland.bar.Payment.Service.PaymentReconcileService;
import com.midland.bar.Payment.Service.PayrollService;
import com.midland.bar.Payment.Service.RevenueShareService;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Utils.PageableParam;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.Responses.ResponsePage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final PaymentReconcileService paymentReconcileService;
    private final RevenueShareService revenueShareService;
    private final PayrollService payrollService;

    /** Money in: every subscription payment that actually completed. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PAYMENTS')")
    @PostMapping("/findPaymentPage")
    public ResponsePage<SubscriptionPayment> findPaymentPage(@RequestBody PageableParam pageableParam) {
        return new ResponsePage<>(subscriptionPaymentRepository.findAll(pageableParam.pageable(false)));
    }

    /** Totals across everything, so the page does not report its own page. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PAYMENTS')")
    @GetMapping("/findPaymentTotals")
    public Response<Map<String, Long>> findPaymentTotals() {
        PaymentTotalsProjection totals = subscriptionPaymentRepository.totals();
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("payments", orZero(totals == null ? null : totals.getPayments()));
        result.put("totalAmount", orZero(totals == null ? null : totals.getTotalAmount()));
        result.put("totalCommission", orZero(totals == null ? null : totals.getTotalCommission()));
        result.put("thisMonth", subscriptionPaymentRepository.amountSince(LocalDate.now().withDayOfMonth(1)));
        return new Response<>(result);
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    /** How a month's takings divide between the people running the platform. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PAYMENTS')")
    @GetMapping("/findRevenueShare")
    public Response<RevenueShareDTO> findRevenueShare(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return revenueShareService.findRevenueShare(year, month);
    }

    /**
     * The same share-out laid out as a payment schedule, to be printed and
     * handed to the bank. Only what is still owed appears on it.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PAYMENTS')")
    @GetMapping("/findPayroll")
    public Response<PayrollDTO> findPayroll(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return payrollService.findPayroll(year, month);
    }

    /** Who is owed one of those shares, and how much each. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PAYMENTS')")
    @GetMapping("/findShareRecipients/{role}")
    public ResponseList<ShareRecipientDTO> findShareRecipients(
            @PathVariable String role,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return revenueShareService.findShareRecipients(role, year, month);
    }

    /** Records that one person's share for the month has been handed over. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('PAY_COMMISSION')")
    @PostMapping("/payShare/{role}/{uid}")
    public Response<Integer> payShare(
            @PathVariable String role,
            @PathVariable String uid,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Integer amount
    ) {
        return revenueShareService.payShare(role, uid, year, month, note, amount);
    }

    /** Payments started and never resolved - the ones worth chasing. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PAYMENTS')")
    @GetMapping("/findUnresolvedPayments")
    public ResponseList<Branch> findUnresolvedPayments() {
        return paymentReconcileService.findUnresolvedPayments();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('RECONCILE_PAYMENTS')")
    @PostMapping("/reconcile/{branchUID}")
    public Response<String> reconcile(@PathVariable String branchUID) {
        return paymentReconcileService.reconcile(branchUID);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_PAYMENTS')")
    @GetMapping("/findBalance")
    public Response<Long> findBalance() {
        return paymentReconcileService.findBalance();
    }
}
