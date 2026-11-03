package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An unpaid staff bill handed from one staff member to another - the waiter
 * going off with a customer still drinking. The bill, its lines and their
 * commission went to the new staff member; this row keeps who it was moved
 * from and to, by whom, and what went with it.
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "bill_transfers", indexes = {
        @Index(name = "idx_bill_transfer_branch_at", columnList = "branch_uid, transferred_at"),
        @Index(name = "idx_bill_transfer_bill", columnList = "bill_uid")
})
public class BillTransfer extends TenantEntity {

    @Column(name = "bill_uid", nullable = false)
    private String billUid;

    /** The bill's code before the move (JM-2) and after it (AK-1). */
    @Column(name = "old_sales_code")
    private String oldSalesCode;

    @Column(name = "new_sales_code")
    private String newSalesCode;

    @Column(name = "from_staff_uid", nullable = false)
    private String fromStaffUid;

    @Column(name = "from_staff_name")
    private String fromStaffName;

    @Column(name = "to_staff_uid", nullable = false)
    private String toStaffUid;

    @Column(name = "to_staff_name")
    private String toStaffName;

    /** The bill's total when it moved. */
    @Column(name = "amount")
    private Integer amount;

    /** The staff commission that moved with it. */
    @Column(name = "commission")
    private Integer commission;

    @Column(name = "reason", length = 300)
    private String reason;

    /** The login that moved it - the staff member's own sign-in, or the cashier's. */
    @Column(name = "transferred_by", nullable = false)
    private String transferredBy;

    @Column(name = "transferred_by_name")
    private String transferredByName;

    @Column(name = "transferred_at", nullable = false)
    private LocalDateTime transferredAt;
}
