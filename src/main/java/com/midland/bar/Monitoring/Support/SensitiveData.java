package com.midland.bar.Monitoring.Support;

import java.util.regex.Pattern;

/**
 * Both logs keep a copy of what was posted, and neither may keep a password.
 * Shared so the two can never drift apart on what counts as sensitive.
 */
public final class SensitiveData {

    // "password": "x"  ->  "password": "***"   (also token, secret, apiKey, ...)
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(\"(?:[^\"]*(?:password|passcode|token|secret|apikey|api_key|authorization|credential)[^\"]*)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    private SensitiveData() {
    }

    /**
     * Masks the values of anything credential-shaped. Deliberately a text
     * match rather than a JSON parse: a malformed body is exactly the one
     * worth recording, and that one will not parse.
     */
    public static String redact(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        return SENSITIVE_FIELD.matcher(payload).replaceAll("$1\"***\"");
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
