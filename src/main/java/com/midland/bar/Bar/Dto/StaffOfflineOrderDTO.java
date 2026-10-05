package com.midland.bar.Bar.Dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** What a staff member wrote on a bill at Staff Sell with no internet - straight onto the bill. */
@Data
public class StaffOfflineOrderDTO {
    private String salesOpenedUID;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String barServiceUID;
        private Integer quantity;
        /** COLD / WARM, as at addItem. */
        private String serving;
    }
}
