package com.midland.bar.Payment.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SnippePaymentResult {
    private boolean success;
    private String reference;
    private String status;
    private String errorCode;
    private String message;
}
