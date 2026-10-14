package com.midland.bar.Bar.Projection;

/**
 * A Setting > Commission row for a service made from more than one stock
 * item: its cost is the whole recipe (the fish and the rice of a wali
 * samaki), so the suggested Stock Purchase share buys all of it back.
 * Everything else is the row as the query read it.
 */
public class RecipeCommissionRow implements ServiceCommissionProjection {

    private final ServiceCommissionProjection row;
    private final int extraCost;
    private final String sourceNames;

    public RecipeCommissionRow(ServiceCommissionProjection row, int extraCost, String sourceNames) {
        this.row = row;
        this.extraCost = extraCost;
        this.sourceNames = sourceNames;
    }

    /** First stock item's share plus the others'. */
    @Override
    public Integer getUnitCost() {
        Integer first = ServiceCommissionProjection.super.getUnitCost();
        if (first == null && extraCost == 0)
            return null;
        return (first == null ? 0 : first) + extraCost;
    }

    /** "Samaki + Mchele". */
    @Override public String getStockSourceName() { return sourceNames; }

    @Override public String getServiceUid() { return row.getServiceUid(); }
    @Override public String getServiceName() { return row.getServiceName(); }
    @Override public String getServiceCode() { return row.getServiceCode(); }
    @Override public String getCategory() { return row.getCategory(); }
    @Override public Integer getPrice() { return row.getPrice(); }
    @Override public Integer getBuyingPrice() { return row.getBuyingPrice(); }
    @Override public Integer getUnitsPerPack() { return row.getUnitsPerPack(); }
    @Override public String getPackUnit() { return row.getPackUnit(); }
    @Override public String getUnit() { return row.getUnit(); }
    @Override public Integer getUnitsPerSale() { return row.getUnitsPerSale(); }
    @Override public String getExtraIngredients() { return row.getExtraIngredients(); }
    @Override public String getCommissionUid() { return row.getCommissionUid(); }
    @Override public Integer getStaffPercent() { return row.getStaffPercent(); }
    @Override public Integer getOwnerPercent() { return row.getOwnerPercent(); }
    @Override public Integer getTraPercent() { return row.getTraPercent(); }
    @Override public Integer getMaintenancePercent() { return row.getMaintenancePercent(); }
    @Override public Integer getEmergencyPercent() { return row.getEmergencyPercent(); }
    @Override public Integer getOtherPercent() { return row.getOtherPercent(); }
    @Override public Integer getRentPercent() { return row.getRentPercent(); }
    @Override public Integer getLoanPercent() { return row.getLoanPercent(); }
    @Override public Integer getLukuPercent() { return row.getLukuPercent(); }
    @Override public Integer getWaterPercent() { return row.getWaterPercent(); }
    @Override public Integer getStockPurchasePercent() { return row.getStockPurchasePercent(); }
    @Override public Integer getTotalPercent() { return row.getTotalPercent(); }
}
