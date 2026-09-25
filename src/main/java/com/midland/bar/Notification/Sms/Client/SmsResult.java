package com.midland.bar.Notification.Sms.Client;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** What the provider said about one message. */
@Getter
@AllArgsConstructor
public class SmsResult {

    private final boolean success;

    /** The provider's own id for the message, when it gives one. */
    private final String reference;

    /** Why it was refused. Null on success. */
    private final String error;

    public static SmsResult sent(String reference) {
        return new SmsResult(true, reference, null);
    }

    public static SmsResult failed(String error) {
        return new SmsResult(false, null, error);
    }
}
