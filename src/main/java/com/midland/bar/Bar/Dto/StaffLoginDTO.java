package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

/** A staff member signing in with their code and PIN, on a device registered to their branch. */
@Getter
@Setter
public class StaffLoginDTO {
    private String deviceToken;
    private String staffCode;
    private String pin;
}
