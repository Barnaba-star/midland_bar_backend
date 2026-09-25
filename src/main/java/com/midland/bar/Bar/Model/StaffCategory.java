package com.midland.bar.Bar.Model;

import java.util.Arrays;

/** What a staff member does at the bar. Stored by name in bar_staffs.bar_category. */
public enum StaffCategory {
    WAITER,
    BARTENDER,
    COOK,
    CASHIER,
    SECURITY,
    CLEANER;

    public static boolean isValid(String value) {
        return value != null && Arrays.stream(values()).anyMatch(c -> c.name().equals(value));
    }
}
