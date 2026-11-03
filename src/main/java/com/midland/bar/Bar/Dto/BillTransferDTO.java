package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Hand an unpaid staff bill to another staff member - its commission goes with it. */
@Data
public class BillTransferDTO {
    @NotBlank(message = "Choose the bill")
    private String billUid;
    @NotBlank(message = "Choose the staff member to take the bill")
    private String toStaffUid;
    /** Optional: why ("anatoka zamu"). */
    private String reason;
}
