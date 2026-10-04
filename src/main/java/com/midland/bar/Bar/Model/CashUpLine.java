package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/** One payment method of a cash-up: expected from the payments, counted by the cashier. */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "cash_up_lines", indexes = {
        @Index(name = "idx_cash_up_line_parent", columnList = "cash_up_uid")
})
public class CashUpLine extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_up_uid", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private CashUp cashUp;

    @Column(name = "method", length = 20, nullable = false)
    private String method;

    /** Payments taken by this method. */
    @Column(name = "takings")
    private Long takings;

    /** Payouts that went by this method - recorded in the system, not typed at the cash-up. */
    @Column(name = "payouts")
    private Long payouts;

    /** Takings minus payouts: what should be in hand. */
    @Column(name = "expected")
    private Long expected;

    @Column(name = "counted")
    private Long counted;

    @Column(name = "variance")
    private Long variance;

    @Column(name = "bills")
    private Integer bills;
}
