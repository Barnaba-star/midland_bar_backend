package com.midland.bar.Payment.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midland.bar.Payment.Client.SnippeClient;
import com.midland.bar.Payment.Service.SubscriptionApplyService;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Receives payment.completed / payment.failed / payment.expired /
 * payment.voided events from Snippe for branch subscription payments
 * started in SettingService.updateSubscription(). This is the ONLY place
 * a subscription actually gets extended - the initial request only ever
 * puts the branch in PENDING, since the customer hasn't authorised the
 * USSD push yet at that point.
 *
 * Must stay publicly reachable (no JWT) - see WebSecurityConfiguration,
 * "/setting/webhooks/**" is permitAll. Trust is established purely via
 * HMAC signature verification below, not authentication.
 */
@RestController
@RequestMapping("/setting/webhooks")
@RequiredArgsConstructor
@Log
public class SnippeWebhookController {

    private final SnippeClient snippeClient;
    private final BranchRepository branchRepository;
    private final SubscriptionApplyService subscriptionApplyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/snippe")
    public ResponseEntity<String> handleSnippeWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature
    ) {
        if (!snippeClient.verifyWebhookSignature(rawBody, timestamp, signature)) {
            log.warning("Rejected Snippe webhook: invalid or missing signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            JsonNode event = objectMapper.readTree(rawBody);
            String type = event.path("type").asText();
            JsonNode data = event.path("data");
            String reference = data.path("reference").asText(null);
            String branchUID = data.path("metadata").path("branchUID").asText(null);

            if (branchUID == null) {
                return ResponseEntity.ok("OK");
            }

            switch (type) {
                case "payment.completed" -> subscriptionApplyService.applyCompletedPayment(branchUID, reference, data);
                case "payment.failed", "payment.expired", "payment.voided" -> subscriptionApplyService.markSubscriptionFailed(branchUID, type, data);
                default -> log.info("Ignoring unhandled Snippe webhook event type: " + type);
            }

        } catch (Exception e) {
            // Log and still return 200 - a payload we can't parse will never
            // succeed on retry either, so let it drop instead of triggering
            // Snippe's retry schedule for nothing.
            log.warning("Failed to process Snippe webhook: " + e.getMessage());
        }

        return ResponseEntity.ok("OK");
    }
}
