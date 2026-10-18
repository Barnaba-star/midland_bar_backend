package com.midland.bar.Bar.Dto;

import lombok.Data;

/** The bar giving up on (part of) a customer's debt, and why. */
@Data
public class DebtWriteOffDTO {
    private String customerUid;
    private Integer amount;
    private String reason;
}
