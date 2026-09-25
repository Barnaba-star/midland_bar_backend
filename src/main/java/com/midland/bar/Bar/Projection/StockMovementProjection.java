package com.midland.bar.Bar.Projection;

/** How one service's stock moved over a period: bought in, used up, and where it stands now. */
public interface StockMovementProjection {
    String getUid();
    String getServiceName();
    String getServiceCode();
    String getCategory();
    String getPackUnit();
    Integer getUnitsPerPack();
    Integer getPrice();
    Integer getStockQuantity();

    /** Units received in the period. */
    Long getPurchasedUnits();

    /** What those deliveries cost. */
    Long getPurchasedCost();

    /** Units sold in the period. */
    Long getUsedUnits();

    /** What those units were sold for (price x quantity as charged). */
    Long getSoldValue();

    /** What those units cost to buy, at the cost copied onto each sale line. */
    Long getSoldCost();

    /** Net correction in the period, in units (negative = lost), and its cost. */
    Long getAdjustedUnits();
    Long getAdjustedValue();

    /** Buying price (per pack) and pack size now, to value what is on hand. */
    Integer getBuyingPrice();
    String getUnit();
    String getUnitLadder();
}
