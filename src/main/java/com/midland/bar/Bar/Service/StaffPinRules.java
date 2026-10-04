package com.midland.bar.Bar.Service;

/**
 * What a staff PIN may be. Four digits, and not one of the handful anyone
 * would try first - the staff code is known to the whole bar, so the PIN is
 * the only thing standing between a colleague and someone else's sales.
 */
public final class StaffPinRules {

    private StaffPinRules() {}

    /** Null when the PIN is fine, otherwise why not. */
    public static String problem(String pin) {
        if (pin == null || !pin.matches("\\d{4}"))
            return "The PIN must be exactly 4 digits";
        if (pin.chars().distinct().count() == 1)
            return "Choose a PIN that is not one digit repeated (like 1111)";
        if ("0123456789".contains(pin) || "9876543210".contains(pin))
            return "Choose a PIN that is not a run of digits (like 1234)";
        return null;
    }
}
