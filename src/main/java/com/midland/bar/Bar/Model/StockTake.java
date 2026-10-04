package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A count of the store: every product counted at one go, what the system said
 * against what was on the shelf. Each difference is also a COUNT adjustment.
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "stock_takes", indexes = {
        @Index(name = "idx_stock_take_branch_at", columnList = "branch_uid, taken_at")
})
public class StockTake extends TenantEntity {

    @Column(name = "taken_by")
    private String takenBy;

    @Column(name = "taken_by_name")
    private String takenByName;

    @Column(name = "taken_at", nullable = false)
    private LocalDateTime takenAt;

    @Column(name = "items_counted")
    private Integer itemsCounted;

    @Column(name = "items_different")
    private Integer itemsDifferent;

    /** Value of what was missing, at buying price - zero or below. */
    @Column(name = "loss_value")
    private Long lossValue;

    /** Value of what was found over - zero or above. */
    @Column(name = "gain_value")
    private Long gainValue;

    @Column(name = "note", length = 500)
    private String note;
}
