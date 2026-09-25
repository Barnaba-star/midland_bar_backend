package com.midland.bar.Bar.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One rung of a stock item's measures: Mshikaki, made of 3 of the rung
 * below. base is filled in by the server - how many of the smallest rung.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LadderLevel {
    private String name;
    private Integer per;
    private Integer base;
}
