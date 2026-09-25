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
