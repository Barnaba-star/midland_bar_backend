package com.midland.bar.Bar.JackPoint;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** A Jack Point as the form sends it; uid set = edit. */
@Data
public class JackPointDTO {
    private String uid;
    @NotBlank(message = "Give the Jack Point a name")
    private String name;
    @NotNull(message = "Enter the commission for each week")
    @Min(value = 1, message = "Enter the commission for each week")
    private Integer weeklyTarget;
    @NotNull(message = "Enter how many weeks in a row")
    @Min(value = 1, message = "Enter how many weeks in a row")
    private Integer weeks;
    /** Any day of the first week; null = this week. */
    private LocalDate startDate;
    private Integer leaveDays;
    private Integer bonusAmount;
    private String rewardNote;
    private Boolean enabled;
}
