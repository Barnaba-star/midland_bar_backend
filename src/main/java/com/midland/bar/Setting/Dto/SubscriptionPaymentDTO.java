package com.midland.bar.Setting.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionPaymentDTO {

    // Mobile network the branch says they're paying with (MPESA, TIGO_PESA,
    // AIRTEL_MONEY, HALOTEL). Snippe auto-detects the real network from the
    // phone number, so this is only used to double-check against that and
    // to show the user what they picked - it is not sent to Snippe.
    @NotBlank(message = "Mobile network is required")
    private String mobileNetwork;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotNull(message = "Number of months is required")
    private Integer months;
}
