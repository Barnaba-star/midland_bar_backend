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
    /** "Nimelipa": the customer says they paid the bar's number - the note says how, for the cashier to check. */
    public static final String PAID = "PAID";
    /**
     * Ordered from the bill's own QR and already sent to the counter / kitchen
     * (owner, 2026-11-10): the waiter is told what, to take it to the customer.
     */
    public static final String ORDER = "ORDER";

    @Column(name = "table_uid", length = 64, nullable = false)
    private String tableUid;

    @Column(name = "table_name", length = 40)
    private String tableName;

    @Column(name = "kind", length = 10, nullable = false)
    private String kind;

    /** For PAID: method, name, reference and amount as the customer typed them. */
    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "called_at", nullable = false)
    private LocalDateTime calledAt;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @Column(name = "done_by", length = 120)
    private String doneBy;

    // ---- "Nimelipa" to the bar's Lipa Namba (owner, 2026-11-11): the bill it pays,
    // what the customer says they sent, and the cashier's answer.
    @Column(name = "bill_uid", length = 64)
    private String billUid;

    @Column(name = "bill_code", length = 30)
    private String billCode;

    @Column(name = "pay_method", length = 20)
    private String payMethod;

    @Column(name = "pay_ref", length = 40)
    private String payRef;

    @Column(name = "pay_amount")
    private Integer payAmount;

    /** CONFIRMED (the cashier took it as payment) or REJECTED (with reason); null while waiting. */
    @Column(name = "outcome", length = 10)
    private String outcome;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

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
