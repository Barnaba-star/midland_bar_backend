package com.midland.bar.Bar.Projection;

import java.math.BigDecimal;

public interface BarServiceRevenueProjection {

    String getServiceUID();

    String getServiceName();

    String getServiceCode();

    String getUsageType();

    BigDecimal getTraAmount();

    BigDecimal getOwnerAmount();

    BigDecimal getStaffAmount();

    BigDecimal getEmergencyAmount();

    BigDecimal getOthersAmount();

    BigDecimal getMaintenanceAmount();
    BigDecimal getLoanAmount();
    BigDecimal getRentAmount();
    BigDecimal getWaterAmount();
    BigDecimal getLukuAmount();
    BigDecimal getStockPurchaseAmount();
}
