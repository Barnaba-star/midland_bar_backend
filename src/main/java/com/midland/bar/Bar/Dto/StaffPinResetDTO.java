package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Staff Sell: a staff member who forgot their PIN sets a new one, with a manager's login as approval. */
@Data
public class StaffPinResetDTO {
    @NotBlank(message = "Weka code ya mhudumu")
    private String staffCode;

    @NotBlank(message = "Weka PIN mpya")
    private String newPin;

    @NotBlank(message = "Weka username ya meneja")
    private String username;

    @NotBlank(message = "Weka neno la siri la meneja")
    private String password;
}
