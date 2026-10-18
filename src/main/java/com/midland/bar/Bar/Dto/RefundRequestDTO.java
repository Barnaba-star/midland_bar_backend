package com.midland.bar.Bar.Dto;

import lombok.Data;

import java.util.List;

/** Asking for money back on a paid bill: which items, how many, how it goes back, and why. */
@Data
public class RefundRequestDTO {
    private String salesOpenedUID;
    private List<Line> lines;
    private String method;
    private String reason;

    @Data
    public static class Line {
        private String barSalesUID;
        private Integer quantity;
    }
}
