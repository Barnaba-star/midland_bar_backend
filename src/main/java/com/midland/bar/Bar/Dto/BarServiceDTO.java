package com.midland.bar.Bar.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** A product on the bar's menu. Field names are kept from the salon's service. */
@Data
public class BarServiceDTO {
    private String uid;
    @NotBlank(message = "Service name is required")
    private String serviceName;


    /** Ignored on input: the code is generated on create (see ProductCategory.codePrefix). */
    private String serviceCode;


    private String description;


    /** Selling price of one unit. Required for a SERVICE; a STOCK_ITEM is not sold. */
    @Min(value = 1, message = "Selling price must be more than 0")
    private Integer price;

    /** SERVICE (default) or STOCK_ITEM. */
    private String kind;

    /**
     * How a SERVICE is counted: "SELF" (its own stock), "NONE" (not
     * counted), or the uid of the STOCK_ITEM it draws on.
     */
    private String stockSource;

    /** With a stock item as source: units of it one sale takes. Worked out from the ladder when saleUnitName is given. */
    @Min(value = 1, message = "Units per sale must be at least 1")
    private Integer unitsPerSale;

    /** A STOCK_ITEM's measures, smallest first: Nyama; Mshikaki = 3; Portion = 10; Nusu = 2; Kilo = 2. */
    private java.util.List<LadderLevel> unitLadder;

    /** With a stock item as source: the rung one sale is (Nusu), and how many of it. */
    private String saleUnitName;

    @Min(value = 1, message = "Enter how many")
    private Integer saleUnitCount;

    @NotBlank(message = "Category is required")
    @Pattern(regexp = "DRINK|FOOD", message = "Category must be DRINK or FOOD")
    private String category;

    /** No longer asked for; kept so older clients' payloads still bind. */
    private String unit;

    /** Blank when stock is bought one unit at a time. */
    private String packUnit;

    /** Required with a pack unit; forced to 1 without one. */
    @Min(value = 1, message = "Units per pack must be at least 1")
    private Integer unitsPerPack;

    /** Per pack when there is one, otherwise per unit. */
    /** Required for anything counted in the store; ignored for a service drawing on a stock item. */
    @Min(value = 0, message = "Buying price cannot be negative")
    private Integer buyingPrice;

    /** Null means the category's default: counted for drinks, not for food. */
    private Boolean trackStock;


    private String usageType;

}
