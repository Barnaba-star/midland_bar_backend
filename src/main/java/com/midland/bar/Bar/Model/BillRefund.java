package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Money given back on a paid bill (owner, 2026-10-18): a cashier asks, with
 * the items and why; a CEO or manager approves (theirs go straight through)
 * or rejects. Approving takes the items off the paid bill the way an open
 * bill's are taken off - stock back, split, pots and commission - and the
 * money leaves the drawer of whoever asked: it comes off their cash-up, by
 * the method it went back by. Back by "deni" lowers the customer's debt.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "bill_refunds", indexes = {
        @Index(name = "idx_bill_refund_branch_status", columnList = "branch_uid, status"),
        @Index(name = "idx_bill_refund_payout", columnList = "requested_by, decided_at")
})
public class BillRefund extends TenantEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "bill_uid", nullable = false)
    private String billUid;

    @Column(name = "sales_code", length = 40)
    private String salesCode;

    /** [{barSalesUID, serviceName, quantity, unitPrice, amount}] as asked. */
    @Column(name = "lines_json", columnDefinition = "TEXT", nullable = false)
    private String linesJson;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** cash, mpesa... or deni (off the customer's debt). */
    @Column(name = "method", length = 20, nullable = false)
    private String method;

    @Column(name = "customer_uid")
    private String customerUid;

    @Column(name = "reason", length = 300, nullable = false)
    private String reason;

    @Column(name = "status", length = 10, nullable = false)
    private String status = PENDING;

    /** Who asked - and so whose drawer the money leaves. */
    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_by_name")
    private String requestedByName;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_by_name")
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;
}
