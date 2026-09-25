package com.midland.bar.Bar.Projection;

import java.time.LocalDate;

public interface StockAndPurchaseProjection {

    String getUid();

    String getPaymentStatus();

    Integer getPayedAmount();

    Integer getTotalAmount();

    Integer getRemainingAmount();

    LocalDate getWeekDate();

    String getCommissionUid();

    String getServiceUid();

    String getServiceName();

    Integer getServicePrice();

    // Stock And Purchase Description
    String getDescriptionUid();

    String getDescriptions();

    LocalDate getDescriptionWeekDate();

    Integer getDescriptionAmount();
}


