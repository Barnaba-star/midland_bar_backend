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

    /*
     * What one sold unit costs, from the row its stock comes out of - the
     * product itself, or the stock item a mshikaki is cut from. Same sum as
     * a sale line's unitCost.
     */
    /** Cost of one pack (one unit when there is no pack). */
    Integer getBuyingPrice();
    Integer getUnitsPerPack();
    String getPackUnit();
    String getUnit();
    /** Units of the stock row one sale takes - 1 unless drawn from a stock item. */
    Integer getUnitsPerSale();
    /** The stock item it is drawn from, null when it is its own stock. */
    String getStockSourceName();

    /** Cost of one sold unit: carton 48,000 / 24 bottles = 2,000. Null when there is no buying price. */
    default Integer getUnitCost() {
        Integer buying = getBuyingPrice();
        if (buying == null || buying <= 0) return null;
        int perPack = getUnitsPerPack() == null || getUnitsPerPack() < 1 ? 1 : getUnitsPerPack();
        int perSale = getUnitsPerSale() == null || getUnitsPerSale() < 1 ? 1 : getUnitsPerSale();
        return Math.round((float) buying * perSale / perPack);
    }

    /**
     * The Stock Purchase share that buys the sold unit back: cost over
     * selling price, rounded up so the pot never falls short (2,000 of
     * 3,000 = 67%). Capped at 100 when it sells at or below cost.
     */
    default Integer getSuggestedStockPercent() {
        Integer cost = getUnitCost();
        Integer price = getPrice();
        if (cost == null || price == null || price <= 0) return null;
        return (int) Math.min(100, (cost * 100L + price - 1) / price);
    }

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
