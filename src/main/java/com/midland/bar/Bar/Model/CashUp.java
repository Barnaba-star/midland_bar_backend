package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A cashier closing their shift: what the payments they took say should be in
 * hand, per method, against what they counted. Kept as it was submitted.
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "cash_ups", indexes = {
        @Index(name = "idx_cash_up_branch_to", columnList = "branch_uid, period_to"),
        @Index(name = "idx_cash_up_cashier", columnList = "cashier_email")
})
public class CashUp extends TenantEntity {

    /** The login that took the payments - BillPayment.receivedBy. */
    @Column(name = "cashier_email", nullable = false)
    private String cashierEmail;

    @Column(name = "cashier_name")
    private String cashierName;

    /** The WorkShift this cash-up counted (null for cash-ups from before shifts). */
    @Column(name = "shift_uid")
    private String shiftUid;

    /** The shift: when it was opened to when it was closed. */
    @Column(name = "period_from", nullable = false)
    private LocalDateTime periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDateTime periodTo;

    /** Payouts the cashier recorded in the system during the shift - taken off expected cash. */
    @Column(name = "payouts_total")
    private Long payoutsTotal;

    @Column(name = "expected_total")
    private Long expectedTotal;

    @Column(name = "counted_total")
    private Long countedTotal;

    /** Counted minus expected: below zero is a shortage. */
    @Column(name = "variance")
    private Long variance;

    @Column(name = "bill_count")
    private Integer billCount;

    @Column(name = "note", length = 500)
    private String note;
}
