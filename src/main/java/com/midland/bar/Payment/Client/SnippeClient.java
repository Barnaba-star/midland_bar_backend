package com.midland.bar.Payment.Client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midland.bar.Payment.Dto.SnippePaymentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client for the Snippe (api.snippe.sh) mobile money payments API.
 * Docs: https://docs.snippe.sh/docs/2026-01-25
 */
@Service
public class SnippeClient {

    @Value("${snippe.base-url}")
    private String baseUrl;

    @Value("${snippe.api-key}")
    private String apiKey;

    @Value("${snippe.webhook-secret}")
    private String webhookSecret;

    @Value("${snippe.webhook-url}")
    private String webhookUrl;

    /**
     * Snippe rejects a payment without customer.email. A subscription is paid
     * by a branch rather than by a person, and not every staff member has an
     * address on file, so the billing address is the platform's own.
     */
    @Value("${snippe.billing-email}")
    private String billingEmail;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initiates a mobile money collection. Snippe auto-detects the network
     * from the phone number - this always comes back with status "pending";
     * the real outcome (completed/failed) arrives later via the
     * payment.completed / payment.failed webhook.
     */
    public SnippePaymentResult createMobilePayment(
            int amountTzs,
            String phoneNumber,
            String firstName,
            String lastName,
            String branchUID,
            int months
    ) {
        try {
            String normalizedPhone = normalizePhoneNumber(phoneNumber);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("amount", amountTzs);
            details.put("currency", "TZS");

            // firstname, lastname and email are all required - a blank any of
            // them comes back as a validation error rather than a payment, so
            // none of them is passed through unchecked.
            Map<String, Object> customer = new LinkedHashMap<>();
            customer.put("firstname", orFallback(firstName, "Midland"));
            customer.put("lastname", orFallback(lastName, "Subscription"));
            customer.put("email", billingEmail);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("branchUID", branchUID);
            metadata.put("months", String.valueOf(months));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("payment_type", "mobile");
            body.put("details", details);
            body.put("phone_number", normalizedPhone);
            body.put("customer", customer);
            body.put("webhook_url", webhookUrl);
            body.put("metadata", metadata);

            String idempotencyKey = buildIdempotencyKey(branchUID);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/payments"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if ("success".equals(json.path("status").asText())) {
                JsonNode data = json.path("data");
                return new SnippePaymentResult(
                        true,
                        data.path("reference").asText(null),
                        data.path("status").asText(null),
                        null,
                        null
                );
            }

            return new SnippePaymentResult(
                    false,
                    null,
                    null,
                    json.path("error_code").asText(null),
                    json.path("message").asText("Payment request failed")
            );

        } catch (Exception e) {
            return new SnippePaymentResult(false, null, null, "client_error", e.getMessage());
        }
    }

    /**
     * Verifies X-Webhook-Signature against the raw request body, per
     * references/webhooks.md: hex(HMAC-SHA256(webhookSecret, "{timestamp}.{rawBody}")),
     * constant-time compared, with a 5 minute freshness window.
     */
    public boolean verifyWebhookSignature(String rawBody, String timestampHeader, String signatureHeader) {
        try {
            if (timestampHeader == null || signatureHeader == null) {
                return false;
            }

            long eventTime = Long.parseLong(timestampHeader);
            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - eventTime) > 300) {
                return false;
            }

            String message = timestampHeader + "." + rawBody;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(computed);

            return constantTimeEquals(expectedSignature, signatureHeader);

        } catch (Exception e) {
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    // Snippe requires 255XXXXXXXXX or +255XXXXXXXXX - local 0XXXXXXXXX is rejected.
    private String normalizePhoneNumber(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            digits = "255" + digits.substring(1);
        }
        if (!digits.startsWith("255")) {
            digits = "255" + digits;
        }
        return digits;
    }

    // Idempotency-Key must be <= 30 chars (references/errors.md: PAY_001 gotcha).
    private String buildIdempotencyKey(String branchUID) {
        String shortUid = branchUID.length() > 8 ? branchUID.substring(0, 8) : branchUID;
        long epochSeconds = System.currentTimeMillis() / 1000;
        return "sub-" + shortUid + "-" + epochSeconds;
    }

    private static String orFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * Asks Snippe what became of a payment we started. This is the fallback
     * the docs point at when a webhook never lands: without it, a customer
     * who paid while the tunnel was down stays locked out and nobody can
     * find out why.
     */
    public SnippePaymentResult getPayment(String reference) {
        if (reference == null || reference.isBlank()) {
            return new SnippePaymentResult(false, null, null, "no_reference", "No payment reference held for this branch");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/payments/" + reference))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if ("success".equals(json.path("status").asText())) {
                JsonNode data = json.path("data");
                return new SnippePaymentResult(
                        true,
                        data.path("reference").asText(reference),
                        data.path("status").asText(null),
                        null,
                        failureReasonOf(data)
                );
            }

            return new SnippePaymentResult(false, reference, null,
                    json.path("error_code").asText(null),
                    json.path("message").asText("Could not read payment"));

        } catch (Exception e) {
            return new SnippePaymentResult(false, reference, null, "client_error", e.getMessage());
        }
    }

    /**
     * The payment object exactly as Snippe holds it, so reconciliation can
     * hand it to the same code the webhook uses. Null when unreachable or
     * unknown.
     */
    public JsonNode getPaymentData(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/payments/" + reference))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            return "success".equals(json.path("status").asText()) ? json.path("data") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Available balance, in TZS. Null when Snippe could not be reached. */
    public Long getAvailableBalance() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/payments/balance"))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (!"success".equals(json.path("status").asText())) {
                return null;
            }
            // Balance comes back as an object, like every other amount in
            // Snippe's responses.
            JsonNode available = json.path("data").path("available");
            JsonNode value = available.isObject() ? available.path("value") : available;
            return value.isNumber() ? value.asLong() : null;

        } catch (Exception e) {
            return null;
        }
    }

    /** Same field-name hunt the webhook does - the reference does not pin it down. */
    private static String failureReasonOf(JsonNode data) {
        for (String field : new String[]{"failure_reason", "failureReason", "reason", "message", "error", "description"}) {
            String value = data.path(field).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

}
