package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** One item written at Staff Sell onto a bill's waiting order. */
@Data
public class StaffOrderItemDTO {
    @NotBlank(message = "Choose the bill")
    private String salesOpenedUID;

    @NotBlank(message = "Choose the service")
    private String barServiceUID;

    @NotNull(message = "Enter how many")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /** COLD or WARM for a drink, as the customer asked; empty = not said. */
    private String serving;
}
