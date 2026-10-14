package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Staff Sell: a change on a bill line already received - the customer wanted
 * a Serengeti Lite instead of the Castle Lite brought. The returned item and
 * the new one go to the station(s) at once, marked as a change.
 */
@Data
public class StaffExchangeDTO {
    /** The bill line (BarSales) going back. */
    @NotBlank(message = "Choose the item going back")
    private String barSalesUID;

    /** How many of it go back; empty = the whole line. */
    private Integer returnQuantity;

    @NotBlank(message = "Choose what the customer wants instead")
    private String barServiceUID;

    @NotNull(message = "Enter how many")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /** COLD or WARM for a drink; empty = not said. */
    private String serving;
}
