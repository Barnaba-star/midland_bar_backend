package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** A delivery to record: packs and/or loose units of one service. */
@Data
public class StockReceiptDTO {
    @NotBlank(message = "Choose the service")
    private String barServiceUID;

    @Min(value = 0, message = "Packs cannot be negative")
    private Integer packs;

    @Min(value = 0, message = "Loose units cannot be negative")
    private Integer looseUnits;

    /** Price of one pack on this delivery; blank keeps the service's current buying price. */
    @Min(value = 0, message = "Price cannot be negative")
    private Integer packPrice;

    @Size(max = 255, message = "Supplier name is too long")
    private String supplier;

    @Size(max = 500, message = "Note is too long")
    private String note;
}
