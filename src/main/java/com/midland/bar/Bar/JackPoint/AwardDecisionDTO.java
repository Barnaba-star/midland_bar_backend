package com.midland.bar.Bar.JackPoint;

import lombok.Data;

import java.time.LocalDate;

/** Approve (with the first paid day off) or turn down (with a reason) a Jack Point win. */
@Data
public class AwardDecisionDTO {
    private boolean approve;
    private LocalDate leaveFrom;
    private String note;
}
