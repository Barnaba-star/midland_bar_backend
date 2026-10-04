package com.midland.bar.Bar.Dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CashUpDTO {
    private List<Count> counts;
    private String note;

    @Getter
    @Setter
    public static class Count {
        private String method;
        private Long counted;
    }
}
