package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One way a bill was paid - a bill paid part cash, part M-Pesa has two. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "bill_payments", indexes = {
        @Index(name = "idx_bill_payments_branch", columnList = "branch_uid"),
        @Index(name = "idx_bill_payments_bill", columnList = "sales_opened_uid")
})
public class BillPayment extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_opened_uid", nullable = false)
    private SalesOpened salesOpened;

    /** cash, mpesa, tigopesa, airtelmoney, halopesa, bank. */
    @Column(name = "method", length = 20)
    private String method;

    /** What this payment put towards the bill. */
    @Column(name = "amount")
    private Integer amount;

    /** Cash handed over, when it was more than the amount. */
    @Column(name = "tendered")
    private Integer tendered;

    /** tendered - amount, given back. */
    @Column(name = "change_given")
    private Integer changeGiven;

    @Column(name = "received_by")
    private String receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}
