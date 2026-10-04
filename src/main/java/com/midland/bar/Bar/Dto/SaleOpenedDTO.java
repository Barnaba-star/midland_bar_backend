package com.midland.bar.Bar.Dto;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaleOpenedDTO {
    private String uid;
    /** A bill opened offline: the uid the device gave it. */
    private String clientUid;
    private String salesCode;
    private String paymentMethod;
    private String status;
    private String paymentStatus;
    private Integer paidAmount;
}
