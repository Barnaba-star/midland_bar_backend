package com.midland.bar.Payment.Dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How one month's takings divide between the people who run the platform. */
@Getter
@Setter
@NoArgsConstructor
public class RevenueShareDTO {

    private int year;
    private int month;

    /** Everything collected in the month. */
    private long revenue;
    private long payments;

    /**
     * What the branch-registering staff earned. Taken from the commission
     * already recorded on each payment rather than recomputed - the figure
     * here and the one on the commission report must be the same number.
     */
    private int staffPercent;
    private long staffAmount;

    /**
     * One share for the role, split between however many hold it - so a
     * second director halves what each takes rather than doubling what the
     * month costs. Root works identically.
     */
    private int directorPercent;
    private int directorCount;
    private long directorAmount;

    private int rootPercent;
    private int rootCount;
    private long rootAmount;

    /** Whatever the three shares leave behind - never below zero. */
    private long operatingAmount;
}
