package com.midland.bar.Bar.Dto;

import lombok.Data;

/** Registering or editing a credit customer. The limit is read only from a CEO or manager. */
@Data
public class DebtCustomerDTO {
    private String name;
    private String phone;
    private String note;
    private Integer creditLimit;
}
