package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BarStaffDTO {
    private String uid;
    /** Typed on the staff form (K1, A2...). Blank gives the next number (001, 002...). */
    private String staffCode;
    /** 4 digits; required for a new staff member, on an edit only when it is being changed. */
    private String pin;
    private String firstName;
    private String middleName;
    private String lastName;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String barServiceUID;
    private String barCategory;
    private String description;
    private String gender;
    private String StoreOpenUID;
}
