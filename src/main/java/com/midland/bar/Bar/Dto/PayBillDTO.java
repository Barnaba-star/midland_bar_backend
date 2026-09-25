package com.midland.bar.Bar.Dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Paying a bill: one or more methods whose amounts add up to the bill. */
@Data
public class PayBillDTO {
    @NotBlank(message = "Choose the bill")
    private String salesOpenedUID;

    @NotEmpty(message = "Add how the bill is paid")
    @Valid
    private List<Part> payments;

    @Data
    public static class Part {
        @NotBlank(message = "Choose the payment method")
        private String method;

        @NotNull(message = "Enter the amount")
        @Min(value = 1, message = "Amount must be more than 0")
        private Integer amount;

        /** Cash only: what the customer handed over, if more than the amount. */
        private Integer tendered;
    }
}
