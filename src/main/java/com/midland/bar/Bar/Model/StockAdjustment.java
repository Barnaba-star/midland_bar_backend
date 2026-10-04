package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A correction to a store count that was not a sale or a delivery: meat
 * that shrank on the grill, a dropped crate, a hand count that came out
 * different. Never edited - a wrong one is put right by the next.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "stock_adjustments", indexes = {
        @Index(name = "idx_stock_adjustments_branch", columnList = "branch_uid"),
        @Index(name = "idx_stock_adjustments_service", columnList = "bar_service_uid")
})
public class StockAdjustment extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_service_uid", nullable = false)
    private BarServiceEntity barService;

    /** REMOVE (took some out, for a reason) or COUNT (set to what was counted). */
    @Column(name = "mode", length = 10)
    private String mode;

    /** See AdjustmentReason. COUNT for a hand count. */
    @Column(name = "reason", length = 20)
    private String reason;

    /** Change to the count, in the smallest unit: negative for a loss, positive for extra found. */
    @Column(name = "units_changed")
    private Integer unitsChanged;

    @Column(name = "stock_before")
    private Integer stockBefore;

    @Column(name = "stock_after")
    private Integer stockAfter;

    /** unitsChanged at the cost of one unit then - negative for a loss. */
    @Column(name = "cost_value")
    private Long costValue;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "adjusted_by")
    private String adjustedBy;

    @Column(name = "adjusted_at")
    private LocalDateTime adjustedAt;

    /** Set when the adjustment came from a full stock take. */
    @Column(name = "stock_take_uid")
    private String stockTakeUid;
}
