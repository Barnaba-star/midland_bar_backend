package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** A correction to one store row, in its smallest unit (the screen adds up the measures). */
@Data
public class StockAdjustmentDTO {
    @NotBlank(message = "Choose the item")
    private String barServiceUID;

    @NotBlank(message = "Choose remove or count")
    @Pattern(regexp = "REMOVE|COUNT", message = "Mode must be REMOVE or COUNT")
    private String mode;

    /** REMOVE: how much came out. COUNT: how much was counted on the shelf. */
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer units;

    /** Required for REMOVE - see AdjustmentReason. */
    private String reason;

    @Size(max = 500, message = "Note is too long")
    private String note;
}
