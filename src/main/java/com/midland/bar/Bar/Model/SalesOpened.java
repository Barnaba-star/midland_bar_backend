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
}
