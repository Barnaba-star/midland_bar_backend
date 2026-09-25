package com.midland.bar.Bar.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** One day on the revenue trend line. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueDTO {
    private LocalDate date;
    private long amount;
}
