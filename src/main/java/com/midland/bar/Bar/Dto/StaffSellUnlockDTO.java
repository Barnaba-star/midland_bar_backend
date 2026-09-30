package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** A manager's login, to leave Staff Sell for POS. */
@Data
public class StaffSellUnlockDTO {
    @NotBlank(message = "Enter the username")
    private String username;

    @NotBlank(message = "Enter the password")
    private String password;
}
