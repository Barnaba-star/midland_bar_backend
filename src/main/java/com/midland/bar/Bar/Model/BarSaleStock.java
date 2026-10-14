package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * What one bill line took from a stock item other than its first one: the
 * rice of a wali samaki (the fish is on the line itself, stockItemUid). The
 * store report counts it as used, and taking the line off puts it back.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "bar_sale_stock", indexes = {
        @Index(name = "idx_bar_sale_stock_line", columnList = "bar_sales_uid"),
        @Index(name = "idx_bar_sale_stock_item_day", columnList = "stock_item_uid, sold_on")
})
public class BarSaleStock extends TenantEntity {

    @Column(name = "bar_sales_uid", nullable = false)
    private String barSalesUid;

    @Column(name = "stock_item_uid", nullable = false)
    private String stockItemUid;

    /** The line's first stock item - its cost includes this one's, so the store report moves it back. */
    @Column(name = "primary_stock_item_uid")
    private String primaryStockItemUid;

    @Column(name = "units", nullable = false)
    private Integer units;

    /** Cost of those units when sold (part of the line's unit cost x quantity). */
    @Column(name = "cost")
    private Integer cost;

    @Column(name = "sold_on")
    private LocalDate soldOn;
}
