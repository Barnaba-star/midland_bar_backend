package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PayStockAndPurchaseDTO {
    private String uid;
    private Integer amount;
    private LocalDate weekDate;
    private String description;
}
