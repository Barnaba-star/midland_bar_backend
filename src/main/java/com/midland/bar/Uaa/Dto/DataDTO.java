package com.midland.bar.Uaa.Dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataDTO {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
