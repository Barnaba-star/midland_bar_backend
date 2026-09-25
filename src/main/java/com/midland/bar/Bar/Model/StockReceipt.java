package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One delivery into the store: 6 crates of Castle Lite from a supplier at a
 * price. Rows are never edited - a mistake is corrected by the next
 * movement - so the store's count can always be traced back through them.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "stock_receipts", indexes = {
        @Index(name = "idx_stock_receipts_branch", columnList = "branch_uid"),
        @Index(name = "idx_stock_receipts_service", columnList = "bar_service_uid")
})
public class StockReceipt extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_service_uid", nullable = false)
    private BarServiceEntity barService;

    /** Whole packs received (crates). 0 for a product bought singly. */
    @Column(name = "packs")
    private Integer packs;

    /** Loose units received on top of the packs. */
    @Column(name = "loose_units")
    private Integer looseUnits;

    /** Pack size at the time - the product's may change later. */
    @Column(name = "units_per_pack")
    private Integer unitsPerPack;

    /** packs x unitsPerPack + looseUnits: what the count went up by. */
    @Column(name = "units_added")
    private Integer unitsAdded;

    /** What one pack cost on this delivery (one unit, with no pack). */
    @Column(name = "pack_price")
    private Integer packPrice;

    /** unitsAdded x packPrice / unitsPerPack. */
    @Column(name = "total_cost")
    private Long totalCost;

    @Column(name = "supplier")
    private String supplier;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "received_by")
    private String receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** The product's count right after this delivery. */
    @Column(name = "stock_after")
    private Integer stockAfter;
}
