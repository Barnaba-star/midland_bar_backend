package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StockTakeDTO {
    private List<Line> lines;
    private String note;

    @Getter
    @Setter
    public static class Line {
        private String barServiceUID;
        /** What is on the shelf, in the smallest unit (bottles). */
        private Integer countedUnits;
    }
}
