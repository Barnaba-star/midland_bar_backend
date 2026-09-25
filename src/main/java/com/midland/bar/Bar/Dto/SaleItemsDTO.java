package com.midland.bar.Bar.Dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Lines to add to an open bill: each a service and how many. */
@Data
public class SaleItemsDTO {
    @NotBlank(message = "Choose the bill")
    private String salesOpenedUID;

    @NotEmpty(message = "Add at least one service")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotBlank(message = "Choose the service")
        private String barServiceUID;

        @NotNull(message = "Enter how many")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }
}
