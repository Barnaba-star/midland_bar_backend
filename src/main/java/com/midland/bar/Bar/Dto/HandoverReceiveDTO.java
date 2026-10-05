package com.midland.bar.Bar.Dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * The cashier took one method's money from a staff member at handover:
 * every bill of theirs in that method, as the summary showed them. If any
 * bill has changed since, nothing is paid.
 */
@Data
public class HandoverReceiveDTO {
    @NotBlank(message = "Enter the staff code")
    private String staffCode;

    @NotBlank(message = "Choose the payment method")
    private String method;

    @NotEmpty(message = "No bills to receive")
    @Valid
    private List<Bill> bills;

    @Data
    public static class Bill {
        @NotBlank
        private String uid;
        @NotNull
        private Long amount;
    }
}
