package com.midland.bar.Bar.Table;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A table calling from the customers' window (owner, 2026-11-09): WAITER
 * ("mwite mhudumu") or BILL ("omba bill"). Open until someone at the bar
 * marks it done.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "table_calls", indexes = {
        @Index(name = "idx_table_calls_branch_open", columnList = "branch_uid, done_at")
})
public class TableCall extends TenantEntity {

    public static final String WAITER = "WAITER";
    public static final String BILL = "BILL";

    @Column(name = "table_uid", length = 64, nullable = false)
    private String tableUid;

    @Column(name = "table_name", length = 40)
    private String tableName;

    @Column(name = "kind", length = 10, nullable = false)
    private String kind;

    @Column(name = "called_at", nullable = false)
    private LocalDateTime calledAt;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @Column(name = "done_by", length = 120)
    private String doneBy;

    /**
     * Written by a customer's phone - nobody is signed in, so the branch is
     * the table's (set before saving), not the login's as TenantEntity does.
     */
    @Override
    @PrePersist
    public void prePersist() {
        if (getBranchUid() == null)
            super.prePersist();
    }
}
