package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "sales_opened", indexes = {
        @Index(
                name = "idx_sales_open_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_sales_open_active",
                columnList = "is_active"
        )
})
public class SalesOpened extends TenantEntity {

    @Column(name = "sales_code")
    private String salesCode;

    /**
     * The bill's own QR (owner, 2026-11-10): the customers' window for this
     * bill only - its copy, ordering onto it, calling its waiter. Random and
     * long, so no bill can be reached by guessing; made the first time asked.
     */
    @Column(name = "customer_token", length = 20)
    private String customerToken;

    /**
     * The staff member the bill belongs to when it was opened at Staff Sell.
     * Everything sold on it is theirs - the seller on each line and the
     * commission - whoever is logged in at the till. Null for bills opened on
     * the Sales page, which sell as the logged-in user as before.
     */
    @Column(name = "staff_uid")
    private String staffUid;

    /** The staff member's name when the bill was opened, for showing on it. */
    @Column(name = "staff_name")
    private String staffName;

    /** Their code when the bill was opened (K1) - what the Sales page filters bills by. */
    @Column(name = "staff_code")
    private String staffCode;

    /** The table a customer ordered from (customers' window) - its orders keep landing on this bill until paid. */
    @Column(name = "table_uid", length = 64)
    private String tableUid;

    @Column(name = "table_name", length = 40)
    private String tableName;

    /**
     * Who opened the bill at the till (sign-in name) - the person a bill with
     * no staff member belongs to on the Sales page: a manager, the CEO, root.
     */
    @Column(name = "opened_by")
    private String openedBy;

    /** Their full name at the time, for showing on the bill. */
    @Column(name = "opened_by_name")
    private String openedByName;

    /*
     * The customer says they paid by phone (or bank): the staff member notes
     * how and under what name, so the cashier can check it at handover. Only
     * a note - the bill is still PENDING until the cashier takes the payment.
     */
    @Column(name = "payment_note_method")
    private String paymentNoteMethod;

    /** The name the money came from (the M-Pesa / Tigo Pesa account holder). */
    @Column(name = "payment_note_payer")
    private String paymentNotePayer;

    /** The transaction reference, when they have it. */
    @Column(name = "payment_note_ref")
    private String paymentNoteRef;

    /** Ofa / deni note: the Madeni customer the bill is for (their name is in paymentNotePayer). */
    @Column(name = "payment_note_customer_uid")
    private String paymentNoteCustomerUid;

    /** The customer who gave their phone + PIN on this bill: it counts towards their loyalty reward. */
    @Column(name = "loyalty_customer_uid")
    private String loyaltyCustomerUid;

    @Column(name = "loyalty_customer_name", length = 80)
    private String loyaltyCustomerName;

    @Column(name = "payment_note_by")
    private String paymentNoteBy;

    @Column(name = "payment_note_at")
    private java.time.LocalDateTime paymentNoteAt;

    /** When the staff member sent this bill for handover from Staff Sell; null = not sent. */
    @Column(name = "handover_sent_at")
    private java.time.LocalDateTime handoverSentAt;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "status")
    private String status = "ACTIVE";

    @Column(name = "payment_status")
    private String paymentStatus="PENDING";

    @Column(name = "paid_amount")
    private Integer paidAmount= 0;

    @Column(name = "bill")
    private Integer bill=0;

    /** Who took the payment, and when. */
    @Column(name = "paid_by")
    private String paidBy;

    @Column(name = "paid_at")
    private java.time.LocalDateTime paidAt;

    /**
     * How the bill was paid, as JSON: [{"method":"cash","amount":10000},
     * {"method":"mpesa","amount":5000}]. The full rows are in bill_payments;
     * this copy lets a list of bills be summed by method without a join.
     */
    @Column(name = "payment_breakdown", columnDefinition = "TEXT")
    private String paymentBreakdown;

    /** TRA: the fiscal receipt number and verification code once a VFD issues one (not yet connected). */
    @Column(name = "tra_receipt_no", length = 60)
    private String traReceiptNo;

    @Column(name = "tra_verify_code", length = 120)
    private String traVerifyCode;
}
