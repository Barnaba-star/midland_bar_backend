package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One item's share of the Other commission from one sale, on the day it was
 * made. The report sums these for any period, so it shows how Other was
 * really split - even after the percentages have been changed.
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "other_commission_entries", indexes = {
        @Index(name = "idx_other_entry_branch_date", columnList = "branch_uid, entry_date"),
        @Index(name = "idx_other_entry_active", columnList = "is_active")
})
public class OtherCommissionEntry extends TenantEntity {

    @Column(name = "item_name", length = 60, nullable = false)
    private String itemName;

    @Column(name = "amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;
}
