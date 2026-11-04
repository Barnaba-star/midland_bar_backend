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
    /** Ofa / deni: the customer's phone and their own PIN ("pin" is masked in the logs). */
    private String phone;
    private String pin;
}
