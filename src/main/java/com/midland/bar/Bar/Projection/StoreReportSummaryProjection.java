package com.midland.bar.Bar.Projection;

import java.time.LocalDate;

public interface StoreReportSummaryProjection {

    String getStoreOpenUid();

    String getOpenStoreCode();

    LocalDate getOpenedDate();

    LocalDate getClosedDate();

    String getStatus();

    Integer getBuyingPrice();

    Integer getTraAmount();

    Integer getEmergencyAmount();

    Integer getStaffAmount();

    Integer getOthersAmount();

    Integer getOwnerAmount();

    Integer getMaintenanceAmount();

    Long getReportCount();

    String getServiceName();

    Integer getTotalPrice();

    Integer getLoanAmount();
    Integer getRentAmount();
    Integer getWaterAmount();
    Integer getLukuAmount();
    Integer getStockPurchaseAmount();
    Integer getSharedAmount();
}

