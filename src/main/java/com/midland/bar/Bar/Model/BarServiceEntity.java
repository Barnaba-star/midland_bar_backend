package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
@Table(name = "bar_services", indexes = {
        @Index(
                name = "idx_bar_services_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_bar_services_active",
                columnList = "is_active"
        )
})
public class BarServiceEntity extends TenantEntity {
    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_code")
    private String serviceCode;

    @Column(name = "description")
    private String description;

    /** Selling price of one unit. */
    @Column(name = "price")
    private Integer price;

    /*
     * A row here is a product on the bar's menu (a Castle Lite, a plate of
     * chips), not a salon service - the class and table keep their old names
     * so the sales and commission-split code that hangs off them is untouched.
     */

    /** DRINK or FOOD - see ProductCategory. */
    @Column(name = "category")
    private String category;

    /** What one unit is - the thing sold one at a time: bottle, glass, plate... */
    @Column(name = "unit")
    private String unit;

    /**
     * What stock is bought in: crate, carton... Null when it is bought one
     * unit at a time. Stock is always counted in units; this is only how it
     * arrives and how the count is shown (5 crates + 23 bottles).
     */
    @Column(name = "pack_unit")
    private String packUnit;

    /** Units in one pack - 24 bottles to a crate. 1 when there is no pack. */
    @Column(name = "units_per_pack")
    private Integer unitsPerPack;

    /**
     * Cost of one pack (one unit when there is no pack), since that is what
     * the supplier's price is quoted in. Cost per unit = this / unitsPerPack.
     */
    @Column(name = "buying_price")
    private Integer buyingPrice;

    /**
     * Units on hand. Starts at 0 and only stock movements (Add Stock, sales)
     * change it - never the product form.
     */
    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    /**
     * False for things made to order (most food), which are sold without a
     * count of units on hand.
     */
    @Column(name = "track_stock")
    private Boolean trackStock;


    /** SERVICE or STOCK_ITEM - see ServiceKind. Null on rows from before, which are services. */
    @Column(name = "kind")
    private String kind;

    /**
     * For a service made from a stock item (a mshikaki from beef): the stock
     * item it draws on. Null when the service is counted itself, or not at all.
     */
    @Column(name = "stock_source_uid")
    private String stockSourceUid;

    /** Units of the stock item one sale takes, in its smallest level: mshikaki 3, nusu 60. */
    @Column(name = "units_per_sale")
    private Integer unitsPerSale;

    /**
     * A stock item's ladder of measures, smallest first, as JSON:
     * [{"name":"Nyama","per":1,"base":1},{"name":"Mshikaki","per":3,"base":3},
     *  {"name":"Portion","per":10,"base":30},...]. "per" is how many of the
     * level below make one; "base" is how many of the smallest. Stock is
     * counted in the smallest level and bought in the largest.
     */
    @Column(name = "unit_ladder", columnDefinition = "TEXT")
    private String unitLadder;

    /** For a service made from a stock item: which level of its ladder one sale is... */
    @Column(name = "sale_unit_name")
    private String saleUnitName;

    /** ...and how many of that level: 1 x Nusu, 2 x Mshikaki. */
    @Column(name = "sale_unit_count")
    private Integer saleUnitCount;

    @Column(name = "status")
    private String status;

    @Column(name = "usage_type")
    private String usageType;

}
