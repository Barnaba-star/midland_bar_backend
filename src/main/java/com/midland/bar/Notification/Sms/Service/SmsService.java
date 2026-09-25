package com.midland.bar.Notification.Sms.Service;

import com.midland.bar.Notification.Sms.Client.SmsClient;
import com.midland.bar.Notification.Sms.Client.SmsResult;
import com.midland.bar.Notification.Sms.Model.SmsLog;
import com.midland.bar.Notification.Sms.Repository.SmsLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import com.midland.bar.Uaa.Support.ActivationCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Everything above the provider: what each message says, who it goes to, and
 * a record that it was tried.
 *
 * Nothing here needs to change when a provider is brought in - that is one
 * implementation of SmsClient.
 */
@Service
@Log
@RequiredArgsConstructor
public class SmsService {

    public static final String TEMPLATE_ACTIVATION_CODE = "ACTIVATION_CODE";

    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";

    /** Off until somebody turns it on, so wiring a provider is still a decision. */
    @Value("${sms.enabled:false}")
    private boolean enabled;

    @Value("${sms.app-name:Midland}")
    private String appName;

    private final SmsClient smsClient;
    private final SmsLogRepository smsLogRepository;

    /**
     * Tells a new user how to sign in for the first time.
     *
     * The code goes in the password box - saying so beats inventing a second
     * field on the login screen that only ever matters once. The body is not
     * kept: it carries a credential, and writing it to a second table would
     * undo the point of hashing it in the first.
     */
    public void sendActivationCode(String uid, String phoneNumber, String username, String code) {
        String body = String.format(
                "Karibu %s. Jina lako la kuingia: %s. Namba ya kuingia mara ya kwanza: %s "
                        + "(iweke sehemu ya neno la siri). Itaisha baada ya saa %d, "
                        + "na utaombwa kuweka neno lako la siri.",
                appName, username, code, ActivationCode.VALID_HOURS
        );
        send(TEMPLATE_ACTIVATION_CODE, uid, phoneNumber, body, false);
    }

    /**
     * @param keepBody whether the text is worth storing. False for anything
     *                 carrying a credential.
     *
     * Runs in its own transaction and never throws: a text that could not be
     * sent must not roll back the thing it was telling somebody about.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(String template, String recipientUid, String phoneNumber, String body, boolean keepBody) {
        SmsLog entry = new SmsLog();
        entry.setSentAt(LocalDateTime.now());
        entry.setTemplate(template);
        entry.setRecipientUid(recipientUid);
        entry.setPhoneNumber(normalise(phoneNumber));
        entry.setBody(keepBody ? body : null);

        try {
            if (!enabled || !smsClient.isConfigured()) {
                entry.setStatus(SKIPPED);
                entry.setError(enabled ? "NO_PROVIDER" : "SMS_DISABLED");
            } else if (entry.getPhoneNumber() == null) {
                // A number nothing can be done with is worth recording as
                // such - silence here reads as "we never tried".
                entry.setStatus(FAILED);
                entry.setError("NO_PHONE_NUMBER");
            } else {
                SmsResult result = smsClient.send(entry.getPhoneNumber(), body);
                entry.setStatus(result.isSuccess() ? SENT : FAILED);
                entry.setReference(result.getReference());
                entry.setError(result.getError());
            }
        } catch (Exception e) {
            entry.setStatus(FAILED);
            entry.setError(truncate(e.getMessage(), 500));
        }

        try {
            smsLogRepository.save(entry);
        } catch (Exception e) {
            log.warning("Could not record SMS attempt: " + e.getMessage());
        }
    }

    /**
     * Tanzanian numbers reach providers as 255XXXXXXXXX. Accepts the forms
     * people actually type - 0712..., +255712..., 255712... - and returns
     * null for anything that cannot be made into one.
     */
    static String normalise(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("255") && digits.length() == 12) {
            return digits;
        }
        if (digits.startsWith("0") && digits.length() == 10) {
            return "255" + digits.substring(1);
        }
        if (digits.length() == 9) {
            return "255" + digits;
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
