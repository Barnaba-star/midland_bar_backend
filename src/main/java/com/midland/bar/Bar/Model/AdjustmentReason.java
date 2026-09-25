package com.midland.bar.Bar.Model;

import java.util.Arrays;

/** Why stock was taken out without being sold. */
public enum AdjustmentReason {
    /** Went off - meat, milk. */
    SPOILED,
    /** Broken - a dropped bottle. */
    BROKEN,
    /** Lost weight cooking, trimming, bones. */
    SHRINKAGE,
    /** Missing, no known cause. */
    LOST,
    /** Used by the house - staff meal, tasting. */
    INTERNAL_USE,
    OTHER;

    public static boolean isValid(String value) {
        return value != null && Arrays.stream(values()).anyMatch(r -> r.name().equals(value));
    }
}
