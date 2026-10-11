package com.midland.bar.Bar.Projection;

/** A staff member's commission over a range of days, added together. */
public interface StaffCommissionTotalProjection {
    String getStaffUid();
    String getFirstName();
    String getMiddleName();
    String getLastName();
    String getBarCategory();
    /** Days in the range with a commission row. */
    Long getDays();
    Long getTotalAmount();
    Long getLossAmount();
    Long getPaidAmount();
    /** Net of all the days - below zero means they owe. */
    Long getRemainingAmount();

    default String getStatus() {
        long remaining = getRemainingAmount() == null ? 0 : getRemainingAmount();
        long paid = getPaidAmount() == null ? 0 : getPaidAmount();
        if (remaining < 0) return "OWES";
        if (remaining == 0) return "PAID";
        return paid == 0 ? "NOT PAID" : "PARTIAL";
    }
}
