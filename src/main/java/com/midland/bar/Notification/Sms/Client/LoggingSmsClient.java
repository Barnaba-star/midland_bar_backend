package com.midland.bar.Notification.Sms.Client;

import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Stands in until a provider is wired in.
 *
 * It writes the message to the log instead of sending it, and reports itself
 * as unconfigured so nothing downstream pretends a text went out. Everything
 * else - templates, delivery records, the hook on user registration - is real
 * and runs through this exactly as it will through the real one.
 *
 * Drops out of the way automatically: define another SmsClient bean and this
 * one is not created.
 */
@Component
@ConditionalOnMissingBean(ignored = LoggingSmsClient.class, value = SmsClient.class)
@Log
public class LoggingSmsClient implements SmsClient {

    @Value("${sms.log-body:false}")
    private boolean logBody;

    @Override
    public SmsResult send(String phoneNumber, String message) {
        // The body is withheld by default - a credentials text would otherwise
        // sit in the application log in clear. sms.log-body=true turns it on
        // for local debugging only.
        log.info("SMS (not sent - no provider configured) to " + phoneNumber
                + (logBody ? ": " + message : " [" + message.length() + " chars]"));
        return SmsResult.failed("NO_PROVIDER");
    }

    @Override
    public boolean isConfigured() {
        return false;
    }
}
