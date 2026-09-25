package com.midland.bar.Payment.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

/**
 * One row per subscription payment that actually completed - the history
 * the branches table cannot hold, since every payment overwrites the same
 * open/close/status columns there.
 *
 * Staff earn a commission on the branches they registered, for as long as
 * those branches keep paying, so this has to answer "which branches paid
 * in month X, how much, and who registered them" for ANY past month - not
 * just the current one.
 *
 * Branch name, staff name and the commission rate are deliberately
 * SNAPSHOT here rather than joined at read time:
 *   - BaseEntity carries @Where(is_active = true), so a soft-deleted
 *     branch or user silently disappears from a join and would take an
 *     already-earned commission row with it.
 *   - a branch can be renamed, or created_by corrected, after the fact.
 *   - changing the commission rate must never rewrite what was already
 *     owed for a month that has been paid out.
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "subscription_payments", indexes = {
        @Index(name = "idx_sub_payment_reference", columnList = "reference", unique = true),
        @Index(name = "idx_sub_payment_paid_at", columnList = "paid_at"),
        @Index(name = "idx_sub_payment_staff", columnList = "staff_uid")
})
public class SubscriptionPayment extends BaseEntity {

    // Snippe's payment reference. Unique - this is what makes recording a
    // payment idempotent when Snippe redelivers the same webhook event.
    @Column(name = "reference", unique = true)
    private String reference;

    @Column(name = "branch_uid", nullable = false)
    private String branchUid;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "branch_code")
    private String branchCode;

    // The user who registered the branch (branches.created_by) at the time
    // this payment landed. Null for branches nobody registered, e.g. ROOT.
    @Column(name = "staff_uid")
    private String staffUid;

    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "amount")
    private Integer amount;

    @Column(name = "months")
    private Integer months;

    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;

    // Rate applied to THIS payment, kept so a later rate change cannot
    // retroactively alter a month that has already been reported or paid.
    @Column(name = "commission_percent")
    private Integer commissionPercent;

    @Column(name = "commission_amount")
    private Integer commissionAmount;
}
