package com.midland.bar.Bar.Dto;

import lombok.Data;

/** A credit customer bringing money in: how much, how, and for cash what was handed over. */
@Data
public class DebtPaymentDTO {
    private String customerUid;
    private String method;
    private Integer amount;
    private Integer tendered;
    /** Phone reference, if any. */
    private String note;
}
