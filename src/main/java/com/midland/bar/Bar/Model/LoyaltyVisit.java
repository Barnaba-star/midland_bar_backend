package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One paid bill that counted towards a customer's loyalty reward. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "loyalty_visits", indexes = {
        @Index(name = "idx_loyalty_visit_customer", columnList = "customer_uid, visit_at"),
        @Index(name = "idx_loyalty_visit_bill", columnList = "bill_uid")
})
public class LoyaltyVisit extends TenantEntity {

    @Column(name = "customer_uid", nullable = false)
    private String customerUid;

    @Column(name = "bill_uid", nullable = false)
    private String billUid;

    @Column(name = "bill_code", length = 40)
    private String billCode;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "visit_at", nullable = false)
    private LocalDateTime visitAt;

    /** The reward this visit helped earn; null while still counting. */
    @Column(name = "reward_uid")
    private String rewardUid;
}
