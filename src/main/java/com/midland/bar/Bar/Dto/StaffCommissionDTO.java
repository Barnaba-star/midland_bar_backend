package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StaffCommissionDTO {
    private String uid;
    private Integer amount;
    private String firstName;
    private String middleName;
    private String lastName;
    private Integer remainingAmount;
    private String filterDate;
    private String filter;
    private LocalDate weekDate;
    private String descriptions;
    /** How it was paid out (cash when not given). */
    private String method;
}
