package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The branch's customer loyalty rule (owner, 2026-11-04): a bill of at least
 * minBill, paid by a customer who gave their phone + PIN, is one visit;
 * billsNeeded visits within periodDays earn rewardAmount as ofa, usable for
 * periodDays. Then the count starts again.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "loyalty_rules", indexes = @Index(name = "idx_loyalty_rule_branch", columnList = "branch_uid"))
public class LoyaltyRule extends TenantEntity {

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    /** A bill of at least this much (TZS) counts. */
    @Column(name = "min_bill", nullable = false)
    private Integer minBill = 100_000;

    @Column(name = "bills_needed", nullable = false)
    private Integer billsNeeded = 5;

    /** The bills must fall within this many days; the reward lasts as long. */
    @Column(name = "period_days", nullable = false)
    private Integer periodDays = 30;

    /** The ofa earned, in TZS. */
    @Column(name = "reward_amount", nullable = false)
    private Integer rewardAmount = 20_000;

    @Column(name = "set_by")
    private String setBy;

    @Column(name = "set_at")
    private LocalDateTime setAt;
}
