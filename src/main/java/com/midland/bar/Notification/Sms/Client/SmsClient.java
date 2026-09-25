package com.midland.bar.Notification.Sms.Client;

/**
 * Sending one text message.
 *
 * Deliberately the whole surface: everything above this - templates, delivery
 * records, retries, which events send at all - is written against this one
 * method, so bringing a provider in means implementing it and nothing else.
 *
 * @see LoggingSmsClient the placeholder that runs until a real one is wired in
 */
public interface SmsClient {

    /**
     * @param phoneNumber in the format the provider expects. SmsService
     *                    normalises Tanzanian numbers to 255XXXXXXXXX before
     *                    this is called.
     * @param message     the finished body; no templating happens below here.
     */
    SmsResult send(String phoneNumber, String message);

    /** False while no provider is configured, so callers can skip the work. */
    boolean isConfigured();
}
