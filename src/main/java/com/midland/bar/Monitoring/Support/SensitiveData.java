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

    // A staff member's code is their secret too (typed at Staff Sell to open
    // their bills), as is their PIN: "staffCode": "1234" -> "staffCode": "***".
    private static final Pattern STAFF_SECRET_FIELD = Pattern.compile(
            "(\"(?:staffCode|newCode|currentCode|pin|newPin|confirmPin|issuedPin)\"\\s*:\\s*)(?:\"[^\"]*\"|\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    // Staff Sell paths carry the code: /bar/staffSell/1234, .../handover/1234/send, /bar/staffOrders/send/1234.
    private static final Pattern STAFF_CODE_IN_PATH = Pattern.compile(
            "(/bar/staffSell/handover/|/bar/staffOrders/send/|/bar/staffSell/(?!handover(?:/|$)|unlock$|openBill$|summary/))(?!receive$)[^/?]+"
    );

    private SensitiveData() {
    }

    // Staff sign-ins were once named after their code ("STAFF-1234"); now after their uid.
    private static final Pattern STAFF_LOGIN_WITH_CODE = Pattern.compile("^STAFF-[0-9A-Za-z]{1,10}$");

    /** A login name, with an old staff sign-in's code taken out. */
    public static String redactLogin(String username) {
        return username != null && STAFF_LOGIN_WITH_CODE.matcher(username).matches() ? "STAFF" : username;
    }

    /** A request path with any staff code in it masked. */
    public static String redactPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        return STAFF_CODE_IN_PATH.matcher(path).replaceAll("$1***");
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
        String masked = SENSITIVE_FIELD.matcher(payload).replaceAll("$1\"***\"");
        return STAFF_SECRET_FIELD.matcher(masked).replaceAll("$1\"***\"");
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
