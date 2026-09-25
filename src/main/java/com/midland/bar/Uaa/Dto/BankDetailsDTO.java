package com.midland.bar.Uaa.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Where somebody's payroll money is sent.
 *
 * All three or none: a number with no bank cannot be paid to, a bank with
 * no number says nothing, and a transfer with no account name is refused at
 * the counter.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankDetailsDTO {
    private String accountNumber;
    private String bankName;
    private String accountName;
}
