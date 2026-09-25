package com.midland.bar.Payment.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Carries no amount on purpose - the server recomputes what is owed. Letting
 * a client name the figure would let it name its own payout.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PayCommissionDTO {
    private String staffUid;
    private Integer year;
    private Integer month;
    private String note;
}
