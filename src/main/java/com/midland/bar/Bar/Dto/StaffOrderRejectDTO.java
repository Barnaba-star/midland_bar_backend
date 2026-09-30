package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Why the supervisor turned an order back - shown to the staff member. */
@Data
public class StaffOrderRejectDTO {
    @NotBlank(message = "Say why the order is rejected")
    @Size(max = 255, message = "Reason is too long")
    private String reason;
}
