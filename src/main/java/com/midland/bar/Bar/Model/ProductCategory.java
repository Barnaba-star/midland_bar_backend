package com.midland.bar.Bar.Model;

/** What a product on the bar's menu is. Stored by name in bar_services.category. */
public enum ProductCategory {
    DRINK,
    FOOD;

    /** Start of the generated product code: DRK-001, FOD-001. */
    public String codePrefix() {
        return this == DRINK ? "DRK" : "FOD";
    }

    /** Stock items share one prefix whatever their category: STK-001. */
    public static final String STOCK_ITEM_PREFIX = "STK";

    /** Food is made to order, so by default it is sold without counting stock. */
    public boolean tracksStockByDefault() {
        return this == DRINK;
    }
}
