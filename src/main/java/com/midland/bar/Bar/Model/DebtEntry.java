package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One line of a credit customer's account. What they owe is every CHARGE
 * less every PAYMENT, WRITE_OFF and REFUND. Amounts are always positive; the kind
 * says which way they go. Nothing here is edited or deleted - a mistake is
 * put right by another line.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "debt_entries", indexes = {
        @Index(name = "idx_debt_entry_customer", columnList = "customer_uid, entry_at"),
        @Index(name = "idx_debt_entry_branch_at", columnList = "branch_uid, entry_at"),
        @Index(name = "idx_debt_entry_by", columnList = "entry_by, entry_at")
})
public class DebtEntry extends TenantEntity {

    public static final String CHARGE = "CHARGE";
    public static final String PAYMENT = "PAYMENT";
    public static final String WRITE_OFF = "WRITE_OFF";
    /** Part of a credit bill given back (an approved BillRefund by deni): owed less, no money moves. */
    public static final String REFUND = "REFUND";

    @Column(name = "customer_uid", nullable = false)
    private String customerUid;

    /** CHARGE (a bill put on credit), PAYMENT (money brought in) or WRITE_OFF (the bar gave up on it). */
    @Column(name = "kind", nullable = false, length = 12)
    private String kind;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** CHARGE: the bill that went on credit. */
    @Column(name = "bill_uid")
    private String billUid;

    @Column(name = "bill_code", length = 40)
    private String billCode;

    /** PAYMENT: how it came in (cash, mpesa...) - it goes into that method of the cashier's cash-up. */
    @Column(name = "method", length = 20)
    private String method;

    @Column(name = "tendered")
    private Integer tendered;

    @Column(name = "change_given")
    private Integer changeGiven;

    /** PAYMENT: phone reference; WRITE_OFF: why. */
    @Column(name = "note", length = 300)
    private String note;

    /** The login that wrote it - a PAYMENT counts in that login's cash-up. */
    @Column(name = "entry_by", nullable = false)
    private String entryBy;

    @Column(name = "entry_by_name")
    private String entryByName;

    @Column(name = "entry_at", nullable = false)
    private LocalDateTime entryAt;
}
