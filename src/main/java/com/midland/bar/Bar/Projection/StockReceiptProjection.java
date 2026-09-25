package com.midland.bar.Bar.Projection;

import java.time.LocalDateTime;

/** One line of a service's delivery history. */
public interface StockReceiptProjection {
    String getUid();
    Integer getPacks();
    Integer getLooseUnits();
    Integer getUnitsPerPack();
    Integer getUnitsAdded();
    Integer getPackPrice();
    Long getTotalCost();
    String getSupplier();
    String getNote();
    String getReceivedBy();
    LocalDateTime getReceivedAt();
    Integer getStockAfter();
}
