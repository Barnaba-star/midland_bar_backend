package com.midland.bar.Bar.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The four numbers at the top of the POS home - what a branch wants to know
 * the moment it opens the app, without reading a chart.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {

    /** Shown on the dashboard so the figures are clearly tied to a branch. */
    private String branchName;

    private long todayRevenue;

    /** Yesterday's total, so today can be shown as a change rather than a bare figure. */
    private long yesterdayRevenue;

    /** Sales left open and unpaid - the one number here that asks to be acted on. */
    private long pendingBillsCount;
    private long pendingBillsAmount;

    private long servicesSoldToday;

    private long openStores;
    private long totalStores;
}
