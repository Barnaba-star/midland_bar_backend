package com.midland.bar.Bar.Table;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An order a customer placed from the table's window (owner, 2026-11-09).
 * PENDING until someone at the bar takes it: ACCEPTED puts it on a waiter's
 * bill for that table and sends it to the counter / kitchen like any staff
 * order (stock and commission when the station receives); REJECTED carries
 * the reason the customer sees. Lines are kept as JSON [{uid,name,qty,price}].
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "table_orders", indexes = {
        @Index(name = "idx_table_orders_branch", columnList = "branch_uid, status"),
        @Index(name = "idx_table_orders_table", columnList = "table_uid, placed_at")
})
public class TableOrder extends TenantEntity {
    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "table_uid", length = 64, nullable = false) private String tableUid;
    @Column(name = "table_name", length = 40) private String tableName;
    @Column(name = "lines_json", length = 4000, nullable = false) private String linesJson;
    @Column(name = "total") private Integer total;
    @Column(name = "note", length = 200) private String note;
    @Column(name = "status", length = 10, nullable = false) private String status = PENDING;
    @Column(name = "placed_at", nullable = false) private LocalDateTime placedAt;
    @Column(name = "decided_at") private LocalDateTime decidedAt;
    @Column(name = "decided_by", length = 120) private String decidedBy;
    @Column(name = "reject_reason", length = 200) private String rejectReason;
    @Column(name = "staff_uid", length = 64) private String staffUid;
    @Column(name = "staff_name", length = 120) private String staffName;
    @Column(name = "bill_uid", length = 64) private String billUid;
    @Column(name = "bill_code", length = 30) private String billCode;

    /** Written by a customer's phone: the table's branch, not a login's. */
    @Override
    @PrePersist
    public void prePersist() {
        if (getBranchUid() == null)
            super.prePersist();
    }
}
