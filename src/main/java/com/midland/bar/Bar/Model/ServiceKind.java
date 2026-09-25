package com.midland.bar.Bar.Model;

/** What a row in bar_services is. Stored by name in bar_services.kind; null reads as SERVICE. */
public enum ServiceKind {
    /** Sold at the counter - Castle Lite, a mshikaki, a robo of nyama choma. */
    SERVICE,
    /**
     * Kept in the store but never sold as-is - beef, goat meat. Services
     * draw on it: a mshikaki takes 1 unit, a robo takes 5.
     */
    STOCK_ITEM;

    public static ServiceKind of(String value) {
        return "STOCK_ITEM".equals(value) ? STOCK_ITEM : SERVICE;
    }
}
