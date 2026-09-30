package com.midland.bar.Payment.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.midland.bar.Setting.Repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Applying a Snippe payment to a branch.
 *
 * Lives here rather than in the webhook controller because two callers need
 * it: the webhook, and reconciliation for payments whose webhook never
 * arrived. Money must be applied the same way whichever route notices it
 * first - two copies would eventually disagree about dates or dedup.
 */
@Service
@Log
@RequiredArgsConstructor
public class SubscriptionApplyService {

    private final BranchRepository branchRepository;
    private final CommissionService commissionService;

    public void applyCompletedPayment(String branchUID, String reference, JsonNode data) {
        branchRepository.findById(branchUID).ifPresent(branch -> {

            if (reference != null && reference.equals(branch.getLastSubscriptionPaymentRef())) {
                // Already applied this exact payment - Snippe redelivered the event.
                return;
            }

            int months = parseMonths(data);
            if (months <= 0) {
                log.warning("payment.completed for branch " + branchUID + " missing/invalid metadata.months");
                return;
            }

            // Record the payment BEFORE overwriting the branch: these columns
            // only ever hold the current state, so once they're rewritten the
            // previous payment is gone. The history lives in
            // subscription_payments and is what staff commission is paid from.
            commissionService.recordPayment(branch, reference, parseAmount(data), months);

            LocalDate base = branch.getCloseSubscription() != null && branch.getCloseSubscription().isAfter(LocalDate.now())
                    ? branch.getCloseSubscription()
                    : LocalDate.now();

            branch.setOpenSubscription(LocalDate.now());
            branch.setCloseSubscription(base.plusMonths(months));
            branch.setSubscriptionStatus("ACTIVE");
            // A paid branch carries no failure any more; leaving the old one
            // would have the login screen explaining a decline that has since
            // been settled.
            branch.setLastPaymentFailure(null);
            branch.setLastSubscriptionPaymentRef(reference);

            branchRepository.save(branch);

            log.info("Extended subscription for branch " + branchUID + " by " + months + " month(s)");
        });
    }

    public void markSubscriptionFailed(String branchUID, String type, JsonNode data) {
        String reason = parseFailureReason(data);

        // Logged in full because the docs do not pin down which field carries
        // the reason - this is how the real shape gets known rather than
        // guessed at a second time.
        log.info("Snippe " + type + " for branch " + branchUID + ": " + data);

        branchRepository.findById(branchUID).ifPresent(branch -> {
            branch.setSubscriptionStatus("FAILED");
            branch.setLastPaymentFailure(truncate(reason, 500));
            branchRepository.save(branch);
        });
    }

    /**
     * Snippe's own wording for the decline. Several field names are tried
     * because the reference does not say which one a failure carries; the
     * event type is the fallback so the row is never left blank.
     */
    public String parseFailureReason(JsonNode data) {
        for (String field : new String[]{"failure_reason", "failureReason", "reason", "message", "error", "description"}) {
            String value = data.path(field).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        JsonNode nested = data.path("failure");
        if (nested.isObject()) {
            String value = nested.path("message").asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    // Snippe echoes back the amount we charged. Null rather than 0 when it's
    // absent, so recordPayment can tell "not reported" from "free" and fall
    // back to the branch's own rate instead of filing a zero. The payment
    // object carries it as {"value": 500, "currency": "TZS"}, like the
    // balance does; a bare number is still accepted.
    public Integer parseAmount(JsonNode data) {
        JsonNode amount = data.path("amount");
        JsonNode value = amount.isObject() ? amount.path("value") : amount;
        return value.isNumber() ? value.asInt() : null;
    }

    public int parseMonths(JsonNode data) {
        try {
            return Integer.parseInt(data.path("metadata").path("months").asText("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
