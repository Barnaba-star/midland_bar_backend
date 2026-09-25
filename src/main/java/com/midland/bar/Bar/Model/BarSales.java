package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "bar_sales", indexes = {
        @Index(
                name = "idx_bar_sales_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_bar_sales_active",
                columnList = "is_active"
        )
})
public class BarSales extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_staff_uid")
    private BarStaff barStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_service_uid")
    private BarServiceEntity barServiceEntity;

    @Column(name = "payment_method")
    private String paymentMethod;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_opened")
    private SalesOpened salesOpened;





    @Column(name = "status")
    private String status = "ACTIVE";

    /*
     * A line is one service times a quantity - 3 x Castle Lite. Prices are
     * copied at the moment of sale so a later price change leaves what was
     * charged, and what it cost, untouched. Lines recorded before these
     * columns existed are one unit each at the service's price.
     */
    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price")
    private Integer unitPrice;

    /** unitPrice x quantity - what the customer is charged for the line. */
    @Column(name = "line_total")
    private Integer lineTotal;

    /** Cost of one unit when sold (buying price / units per pack). */
    @Column(name = "unit_cost")
    private Integer unitCost;

    /**
     * The store row the line's units came out of - the service itself
     * (Castle Lite), the stock item it is made from (beef, for a mshikaki),
     * or null when nothing is counted.
     */
    @Column(name = "stock_item_uid")
    private String stockItemUid;

    /** Units taken from that store row: quantity x units per sale. */
    @Column(name = "stock_units")
    private Integer stockUnits;

    /** Email of the logged-in user who rang the line up. */
    @Column(name = "sold_by")
    private String soldBy;
}
