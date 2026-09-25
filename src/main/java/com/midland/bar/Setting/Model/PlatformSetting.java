package com.midland.bar.Setting.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The numbers that run the platform itself, as opposed to one bar.
 *
 * Exactly one row: these are not per-branch, and a single row keeps them
 * typed and validated rather than a bag of strings. Every default below is
 * the value that was previously hard-coded, so turning this on changes
 * nothing until somebody edits it - with one deliberate exception, trialDays,
 * which closes the hole where a branch that was never given a plan used the
 * system free forever.
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "platform_settings")
public class PlatformSetting extends BaseEntity {

    /** Share of each completed subscription payment that goes to the staff member who registered the branch. */
    @Column(name = "commission_percent")
    private Integer commissionPercent = 10;

    /**
     * Share of a month's takings the DIRECTOR role is due in total, divided
     * between however many hold it. Not a percentage each: that made the
     * month cost more with every appointment, without limit.
     */
    @Column(name = "director_percent")
    private Integer directorPercent = 20;

    /** Share of a month's takings the ROOT role is due, split the same way. */
    @Column(name = "root_percent")
    private Integer rootPercent = 25;

    /** Free days a newly registered branch gets before it has to pay. */
    @Column(name = "trial_days")
    private Integer trialDays = 30;

    /** Days a branch may keep logging in after its subscription lapses. 0 = cut off immediately, which is the old behaviour. */
    @Column(name = "grace_period_days")
    private Integer gracePeriodDays = 0;

    /** Smallest subscription payment Snippe will be asked to collect, in TZS. */
    @Column(name = "minimum_payment_amount")
    private Integer minimumPaymentAmount = 500;

    /** Pre-filled on a new branch so an admin does not type the plan every time. 0 = no default. */
    @Column(name = "default_subscription_amount")
    private Integer defaultSubscriptionAmount = 0;

    @Column(name = "default_subscription_days")
    private Integer defaultSubscriptionDays = 30;

    /** How long a login lasts before the user has to sign in again. */
    @Column(name = "session_hours")
    private Integer sessionHours = 24;

    /** Days an error stays in the Settings > Errors list. */
    @Column(name = "error_retention_days")
    private Integer errorRetentionDays = 30;

    /** Days before a cleared error is deleted for good. */
    @Column(name = "error_purge_days")
    private Integer errorPurgeDays = 90;

    /** Days an audit entry is kept. Longer than errors - it is a record, not a symptom. */
    @Column(name = "audit_retention_days")
    private Integer auditRetentionDays = 365;
}
