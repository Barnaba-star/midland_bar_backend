package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

/** How a customer says they paid a bill (method + payer name + reference). Empty method clears it. */
@Getter
@Setter
public class PaymentNoteDTO {
    private String method;
    private String payerName;
    private String reference;
    /** Ofa / deni: the Madeni customer, and the code they gave the waiter. */
    private String customerUid;
    private String customerCode;
}
