package com.midland.bar.Payment.Projection;

public interface StaffEarningProjection {
    String getStaffUid();
    String getStaffName();
    Long getBranchesPaid();
    Long getTotalCollected();
    Long getCommissionDue();
    Long getPayments();
}
