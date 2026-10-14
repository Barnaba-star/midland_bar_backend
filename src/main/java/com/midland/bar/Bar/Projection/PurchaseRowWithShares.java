package com.midland.bar.Bar.Projection;

import java.time.LocalDate;
import java.util.List;

/**
 * A Stock & Purchase row of a recipe (wali samaki = fish + rice): the pot is
 * the service's, but what it buys back is split by what each stock item
 * costs, so the owner sees how much is for the fish and how much for the
 * rice. Shares follow today's buying prices.
 */
public class PurchaseRowWithShares implements StockAndPurchaseProjection {

    /** One stock item's part of the pot. */
    public record Share(String stockItemUid, String name, int percent, int totalAmount, int remainingAmount) {}

    private final StockAndPurchaseProjection row;
    private final List<Share> shares;

    public PurchaseRowWithShares(StockAndPurchaseProjection row, List<Share> shares) {
        this.row = row;
        this.shares = shares;
    }

    public List<Share> getIngredientShares() { return shares; }

    @Override public String getUid() { return row.getUid(); }
    @Override public String getPaymentStatus() { return row.getPaymentStatus(); }
    @Override public Integer getPayedAmount() { return row.getPayedAmount(); }
    @Override public Integer getTotalAmount() { return row.getTotalAmount(); }
    @Override public Integer getRemainingAmount() { return row.getRemainingAmount(); }
    @Override public LocalDate getWeekDate() { return row.getWeekDate(); }
    @Override public String getCommissionUid() { return row.getCommissionUid(); }
    @Override public String getServiceUid() { return row.getServiceUid(); }
    @Override public String getServiceName() { return row.getServiceName(); }
    @Override public Integer getServicePrice() { return row.getServicePrice(); }
    @Override public String getDescriptionUid() { return row.getDescriptionUid(); }
    @Override public String getDescriptions() { return row.getDescriptions(); }
    @Override public LocalDate getDescriptionWeekDate() { return row.getDescriptionWeekDate(); }
    @Override public Integer getDescriptionAmount() { return row.getDescriptionAmount(); }
}
