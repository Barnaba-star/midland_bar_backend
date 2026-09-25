package com.midland.bar.Bar.Dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreDTO {
    private String uid;
    private String nameOfStore;

    private String codeOfStore;

    private Integer quantity;

    private String barServiceEntityUID;

    private String description;

    private Integer buyingPrice;

    private String openStoreUID;
}
