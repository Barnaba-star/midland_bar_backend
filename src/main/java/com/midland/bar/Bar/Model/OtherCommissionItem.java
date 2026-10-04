package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Where a branch's "Other" commission goes: one named share of it - the
 * internet, the decoder, the guards, fuel for the managers' cars. A branch's
 * shares add up to exactly 100; with none set, Other stays one pot as before.
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "other_commission_items", indexes = {
        @Index(name = "idx_other_item_branch", columnList = "branch_uid"),
        @Index(name = "idx_other_item_active", columnList = "is_active")
})
public class OtherCommissionItem extends TenantEntity {

    @Column(name = "name", length = 60, nullable = false)
    private String name;

    /** Its share of Other, in whole percent. */
    @Column(name = "percent", nullable = false)
    private Integer percent;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
