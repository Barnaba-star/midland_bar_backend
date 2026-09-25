package com.midland.bar.Payment.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One person on the payroll, and what the bank should send them. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayrollLineDTO {

    private String uid;

    /** ROOT, DIRECTOR or STAFF - how they came to be owed this. */
    private String role;

    private String name;

    private String phone;

    /** Which branch they belong to, for a sheet covering more than one. */
    private String branchName;

    /** Where the money goes - set by its owner, on their own profile. */
    private String accountNumber;

    /** And which bank it is at; an account number alone cannot be paid to. */
    private String bankName;

    /** The name the account is held in, which the bank checks against. */
    private String accountName;

    /** What the month earned them. */
    private long earned;

    /** What has already been recorded as settled. */
    private long paid;

    /** What the bank is being asked to send: earned less already paid. */
    private long toPay;
}
