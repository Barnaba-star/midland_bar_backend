package com.midland.bar.Bar.Dto;

import lombok.Data;

/** Take some or all of one line off an unpaid bill. */
@Data
public class RemoveBillLineDTO {
    /** The BarSales line. */
    private String barSalesUID;
    /**
     * Or, for a line added offline (its uid unknown to the device): the op id
     * that added it, the service, and the bill.
     */
    private String addOpId;
    private String barServiceUID;
    private String salesOpenedUID;
    /** How many to take off; empty means the whole line. */
    private Integer quantity;
    /** Required - kept with the record. */
    private String reason;
}
