package com.midland.bar.Bar.Projection;

import jakarta.persistence.Column;

import java.math.BigDecimal;

public interface CommissionProjection {

    /*
     * ==========================================================
     * COMMISSION PROJECTION
     * Used to retrieve Commission Method information.
     * ==========================================================

     */

    String getUid();

    String getServiceName();

    Integer getPrice();

    BigDecimal getStaffPercent();

    BigDecimal getOwnerPercent();

    BigDecimal  getTraPercent();

    BigDecimal getEmergencyPercent();

    BigDecimal getTotalPercent();

    BigDecimal getMaintenancePercent();

    BigDecimal getOtherPercent();

    BigDecimal getLukuPercent();

    BigDecimal getWaterPercent();

    BigDecimal getRentPercent();
    BigDecimal getLoanPercent();

    BigDecimal getStockPurchasePercent();
}
