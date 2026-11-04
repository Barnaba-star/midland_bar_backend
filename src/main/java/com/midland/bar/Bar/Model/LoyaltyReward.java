package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Ofa a customer earned by their visits; spent through OfferUse rows that point at it. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "loyalty_rewards", indexes = @Index(name = "idx_loyalty_reward_customer", columnList = "customer_uid, expires_at"))
public class LoyaltyReward extends TenantEntity {

    @Column(name = "customer_uid", nullable = false)
    private String customerUid;

    @Column(name = "customer_name", length = 80)
    private String customerName;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "visits", nullable = false)
    private Integer visits;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
