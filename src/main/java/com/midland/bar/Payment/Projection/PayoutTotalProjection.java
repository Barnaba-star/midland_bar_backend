package com.midland.bar.Payment.Projection;

import java.time.LocalDate;

public interface PayoutTotalProjection {
    String getStaffUid();
    Long getAmountPaid();
    LocalDate getLastPaidAt();
}
