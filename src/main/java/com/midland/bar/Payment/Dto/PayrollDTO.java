package com.midland.bar.Payment.Dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** A month's payment schedule, in the shape it is taken to the bank. */
@Getter
@Setter
@NoArgsConstructor
public class PayrollDTO {

    /**
     * Whose payroll this is - the platform's own branch, the one that
     * collects the subscriptions and pays these shares out. Printed at the
     * top, because a sheet handed to a bank has to say who is instructing it.
     */
    private String companyName;

    private int year;
    private int month;

    /** Stamped on the printed sheet, so two copies can be told apart. */
    private LocalDateTime generatedAt;

    /** The month's subscription income the shares were worked out from. */
    private long revenue;

    /**
     * The split the lines below come from - what the month gave each role
     * before it is broken down by person. Without it the page shows totals
     * with nothing to check them against.
     */
    private RevenueShareDTO share;

    /** What the month owes in total, before anything already settled. */
    private long totalEarned;

    /** What has already gone out against this month. */
    private long totalPaid;

    /** What is left to send, once payments already recorded are taken off. */
    private long totalToPay;

    private int recipients;

    /** How many of those have already been settled in full. */
    private int settled;

    /**
     * How many of those have no phone number on file. A bank needs one, so
     * this is worth saying out loud before the sheet is printed rather than
     * discovered at the counter.
     */
    private int missingPhone;

    /**
     * And how many of those still owed money have no account number. The
     * sheet cannot instruct a transfer without one.
     */
    private int missingAccount;

    private List<PayrollLineDTO> lines;
}
