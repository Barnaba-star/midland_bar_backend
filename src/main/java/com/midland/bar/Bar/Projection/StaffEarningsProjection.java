package com.midland.bar.Bar.Projection;

/** One staff member's share of the branch's takings over a period. */
public interface StaffEarningsProjection {
    String getStaffUid();
    String getFirstName();
    String getLastName();
    Long getEarned();
    Long getServicesDone();
}
