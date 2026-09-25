package com.midland.bar.Bar.Projection;

import java.time.LocalDateTime;

/** One line of an item's adjustment history. */
public interface StockAdjustmentProjection {
    String getUid();
    String getMode();
    String getReason();
    Integer getUnitsChanged();
    Integer getStockBefore();
    Integer getStockAfter();
    Long getCostValue();
    String getNote();
    String getAdjustedBy();
    LocalDateTime getAdjustedAt();
}
