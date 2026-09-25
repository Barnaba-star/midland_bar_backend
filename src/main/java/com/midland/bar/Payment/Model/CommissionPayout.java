package com.midland.bar.Payment.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

/**
 * A commission payout actually handed to a staff member for one month.
 *
 * Deliberately an amount rather than a "paid" flag: a month can still be
 * running when you pay, and another branch can pay into that same month
 * afterwards. Recording what was handed over lets the report show
 * due - paid = outstanding, so a top-up is a second row rather than a
 * choice between paying twice and not paying the remainder at all.
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "commission_payouts", indexes = {
        @Index(name = "idx_commission_payout_period", columnList = "staff_uid, period_year, period_month")
})
public class CommissionPayout extends BaseEntity {

    /**
     * Which share this payout settles: STAFF, DIRECTOR or ROOT. One ledger
     * for all three rather than a table each - the same question is being
     * answered, and a second table would drift.
     *
     * Rows written before this column existed are staff commission, which is
     * why every read treats null as STAFF.
     */
    @Column(name = "role_code", length = 20)
    private String roleCode = "STAFF";

    @Column(name = "staff_uid", nullable = false)
    private String staffUid;

    // Snapshot, same reasoning as SubscriptionPayment: a soft-deleted user
    // must not take the record of what they were paid with them.
    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;

    // Who authorised it - this is money leaving the business.
    @Column(name = "paid_by_uid")
    private String paidByUid;

    @Column(name = "paid_by_name")
    private String paidByName;

    @Column(name = "note", length = 500)
    private String note;
}
