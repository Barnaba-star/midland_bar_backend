package com.midland.bar.Payment.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Payment.Client.SnippeClient;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The safety net for payments whose webhook never arrived.
 *
 * Snippe gives up after five delivery attempts, and the webhook URL here is
 * a tunnel that changes. A payment that completed while nobody was listening
 * leaves a customer who has paid locked out, with no sign of it anywhere.
 * This asks Snippe directly and applies the answer.
 */
@Service
@Log
@RequiredArgsConstructor
public class PaymentReconcileService {

    private final BranchRepository branchRepository;
    private final SnippeClient snippeClient;
    private final SubscriptionApplyService subscriptionApplyService;

    /** Branches holding a payment that was started and never resolved. */
    public ResponseList<Branch> findUnresolvedPayments() {
        return new ResponseList<>(branchRepository.findUnresolvedPayments());
    }

    /**
     * Asks Snippe what became of one branch's outstanding payment and applies
     * it through the same path the webhook uses - never a second copy of the
     * rules about dates, dedup or commission.
     */
    @Transactional
    public Response<String> reconcile(String branchUID) {
        Branch branch = branchRepository.findById(branchUID).orElse(null);
        if (branch == null) {
            return new Response<>("Branch Not Found");
        }

        String reference = branch.getPendingPaymentRef();
        if (reference == null || reference.isBlank()) {
            return new Response<>("No payment reference held for this branch");
        }

        JsonNode data = snippeClient.getPaymentData(reference);
        if (data == null) {
            return new Response<>("Could not reach Snippe for this payment");
        }

        String status = data.path("status").asText("");
        log.info(LoggerUser.getEmail() + " reconciled " + reference + " for branch " + branchUID + ": " + status);

        switch (status) {
            case "completed", "success" -> {
                subscriptionApplyService.applyCompletedPayment(branchUID, reference, data);
                return new Response<>("Payment confirmed and subscription extended");
            }
            case "failed", "expired", "voided", "cancelled" -> {
                subscriptionApplyService.markSubscriptionFailed(branchUID, "reconcile." + status, data);
                return new Response<>("Snippe reports this payment did not go through");
            }
            default -> {
                return new Response<>("Snippe still has this payment as " + status);
            }
        }
    }

    /** Available balance at Snippe, or null when it could not be read. */
    public Response<Long> findBalance() {
        return new Response<>(snippeClient.getAvailableBalance());
    }
}
