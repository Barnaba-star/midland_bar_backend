package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class BarBookingDTO {
    private String uid;
    private String customerName;
    private LocalDate bookingDate;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private String status;
    private List<String> servicesUID;

}
