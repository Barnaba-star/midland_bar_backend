package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SpendDTO {
    private String uid;
    private Integer amount;
    private String description;
    /** How it was paid out (cash when not given). */
    private String method;
}
