package com.midland.bar.Setting.Dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** A branch whose subscription is running out, or has already run out. */
@Getter
@Setter
@NoArgsConstructor
public class ExpiringBranchDTO {

    private String uid;
    private String branchName;
    private String branchCode;
    private String region;
    private String phone;

    private LocalDate closeSubscription;

    /** Negative once it has lapsed - how many days ago, rather than how many left. */
    private long daysLeft;

    private String subscriptionStatus;
    private Integer subscriptionAmount;

    /** Whoever registered it, so the right person can chase it. */
    private String registeredBy;

    /** Why the last payment failed, when one did. */
    private String lastPaymentFailure;
}
