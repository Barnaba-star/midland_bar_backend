package com.midland.bar.Bar.Dto;

import com.midland.bar.Bar.Model.BarServiceEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BarSalesDTO {
    private String uid;
    private String barStaffUID;
    private List<String> barServiceUID;
    private String paymentMethod;
    private String salesOpenedUID;
}
