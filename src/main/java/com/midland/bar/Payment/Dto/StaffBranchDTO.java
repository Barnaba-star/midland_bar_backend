package com.midland.bar.Payment.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One branch a staff member registered, told from the point of view of a
 * single month: did it pay, how much, and what is it owed on.
 *
 * The branches that did NOT pay are the point of this view - the report
 * totals can only ever show what came in, and a staff member chasing their
 * commission needs to know which of their branches to go and talk to.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffBranchDTO {

    private String branchUid;
    private String branchName;
    private String branchCode;
    private String region;

    private LocalDate registeredAt;

    // Current subscription state of the branch (ACTIVE, FREE, EXPIRED...),
    // which explains WHY a branch did not pay this month.
    private String subscriptionStatus;
    private LocalDate closeSubscription;

    // ---- the reported month
    private boolean paid;
    private int payments;
    private long amountPaid;
    private long commission;
    private LocalDate lastPaidAt;

    // True when the branch no longer exists but still has payments in this
    // month - the commission was earned and must still be visible.
    private boolean deleted;
}
