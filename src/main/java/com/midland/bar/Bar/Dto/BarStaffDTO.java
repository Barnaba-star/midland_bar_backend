package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BarStaffDTO {
    private String uid;
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
