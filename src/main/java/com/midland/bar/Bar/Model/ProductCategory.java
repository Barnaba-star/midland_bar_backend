package com.midland.bar.Bar.Model;

/** What a product on the bar's menu is. Stored by name in bar_services.category. */
public enum ProductCategory {
    DRINK,
    FOOD,
    /** Neither drink nor food (owner, 2026-10-14): shisha and what it is made from. */
    SHISHA,
    /** A game on the pool table, paid per game or by time - received at the pool table (POOL). */
    POOL;

    /** Start of the generated product code: DRK-001, FOD-001, SHS-001, POL-001. */
    public String codePrefix() {
        return switch (this) {
            case DRINK -> "DRK";
            case FOOD -> "FOD";
            case SHISHA -> "SHS";
            case POOL -> "POL";
        };
    }

    /** Stock items share one prefix whatever their category: STK-001. */
    public static final String STOCK_ITEM_PREFIX = "STK";

    /** Only drinks are counted by default: food and shisha are made to order, a pool game is no stock at all. */
    public boolean tracksStockByDefault() {
        return this == DRINK;
    }
}
