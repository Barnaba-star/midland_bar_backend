package com.midland.bar.Bar.Projection;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


public interface CommissionTotalProjection {

    Integer getTraAmount();

    Integer getEmergencyAmount();

    Integer getStaffAmount();

    Integer getOthersAmount();

    Integer getOwnerAmount();

    Integer getMaintenanceAmount();

    Integer getLukuAmount();

    Integer getWaterAmount();

    Integer getRentAmount();
    Integer getLoanAmount();

    Integer getStockPurchaseAmount();
}


