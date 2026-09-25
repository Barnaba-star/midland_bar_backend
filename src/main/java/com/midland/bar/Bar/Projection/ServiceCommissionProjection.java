package com.midland.bar.Bar.Projection;

/**
 * A service with its commission split, if it has one. Every commission
 * field is null for a service nobody has configured yet.
 */
public interface ServiceCommissionProjection {
    String getServiceUid();
    String getServiceName();
    String getServiceCode();
    String getCategory();
    Integer getPrice();

    String getCommissionUid();
    Integer getStaffPercent();
    Integer getOwnerPercent();
    Integer getTraPercent();
    Integer getMaintenancePercent();
    Integer getEmergencyPercent();
    Integer getOtherPercent();
    Integer getRentPercent();
    Integer getLoanPercent();
    Integer getLukuPercent();
    Integer getWaterPercent();
    Integer getStockPurchasePercent();
    Integer getTotalPercent();
}
