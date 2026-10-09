package com.midland.bar.Bar.Dto;

import lombok.Data;

/** A staff member's handover shortage: what the bills came to and what was handed in. */
@Data
public class StaffLossDTO {
    private String staffCode;
    private Integer expectedAmount;
    private Integer handedAmount;
    private String note;
    /** The payment method that came in short; cash when left out. */
    private String method;
}
