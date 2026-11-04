package com.midland.bar.Bar.Dto;

import lombok.Data;

/** A customer naming themselves at the till: phone + their own PIN ("pin" is masked in the logs); name only when registering. */
@Data
public class LoyaltyPinDTO {
    private String name;
    private String phone;
    private String pin;
}
