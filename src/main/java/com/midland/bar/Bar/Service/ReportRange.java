package com.midland.bar.Bar.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/** The Reports filters as a [from, to) time range: DAY, YESTERDAY, WEEK, LAST_WEEK, MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR, or a date. */
public final class ReportRange {

    private ReportRange() {}

    public static LocalDateTime[] of(String filter) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String f = filter == null ? "DAY" : filter.toUpperCase();
        LocalDate[] d = switch (f) {
            case "YESTERDAY" -> new LocalDate[]{today.minusDays(1), today};
            case "WEEK", "THIS_WEEK" -> new LocalDate[]{monday, monday.plusWeeks(1)};
            case "LAST_WEEK" -> new LocalDate[]{monday.minusWeeks(1), monday};
            case "MONTH", "THIS_MONTH" -> new LocalDate[]{today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1)};
            case "LAST_MONTH" -> new LocalDate[]{today.withDayOfMonth(1).minusMonths(1), today.withDayOfMonth(1)};
            case "THIS_YEAR", "YEAR" -> new LocalDate[]{today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1)};
            case "LAST_YEAR" -> new LocalDate[]{today.withDayOfYear(1).minusYears(1), today.withDayOfYear(1)};
            case "DAY", "TODAY" -> new LocalDate[]{today, today.plusDays(1)};
            default -> {
                LocalDate day = LocalDate.parse(filter);
                yield new LocalDate[]{day, day.plusDays(1)};
            }
        };
        return new LocalDateTime[]{d[0].atStartOfDay(), d[1].atStartOfDay()};
    }
}
