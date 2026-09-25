package com.midland.bar.Payment.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of the commission report: a staff member, how many branches they
 * have registered in total, and how many of those actually paid in the month
 * being reported on.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StaffCommissionDTO {
    private String staffUid;
    private String staffName;
    private String username;

    // every branch this staff member has ever registered
    private long branchesRegistered;

    // of those, how many paid within the reported month
    private long branchesPaid;

    // number of payments in the month (a branch can pay more than once)
    private long payments;

    private long totalCollected;
    private long commissionDue;

    // what has already been handed to this staff member for the month
    private long commissionPaid;

    // due - paid; what the Pay button would hand over right now
    private long outstanding;

    private java.time.LocalDate lastPaidAt;
}
