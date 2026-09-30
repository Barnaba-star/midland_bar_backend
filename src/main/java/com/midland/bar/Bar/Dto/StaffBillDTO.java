package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Open a bill for a staff member at Staff Sell. Its code comes from theirs: K1-1, K1-2... */
@Data
public class StaffBillDTO {
    @NotBlank(message = "Enter the staff code")
    private String staffCode;
}
