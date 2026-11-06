package com.midland.bar.Bar.JackPoint;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * How long one round of a Jack Point lasts: a day, a week (Monday to Sunday)
 * or a calendar month. A game counts rounds in a row at or over its target.
 */
public final class JackPeriod {

    public static final String DAY = "DAY";
    public static final String WEEK = "WEEK";
    public static final String MONTH = "MONTH";

    private JackPeriod() {}

    /** DAY, WEEK or MONTH; anything else (and the games from before periods) is WEEK. */
    public static String of(String p) {
        if (p == null)
            return WEEK;
        String u = p.trim().toUpperCase();
        return DAY.equals(u) || MONTH.equals(u) ? u : WEEK;
    }

    /** The first day of the round [day] falls in. */
    public static LocalDate start(LocalDate day, String period) {
        return switch (of(period)) {
            case DAY -> day;
            case MONTH -> day.withDayOfMonth(1);
            default -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        };
    }

    public static LocalDate plus(LocalDate start, String period, long n) {
        return switch (of(period)) {
            case DAY -> start.plusDays(n);
            case MONTH -> start.plusMonths(n);
            default -> start.plusWeeks(n);
        };
    }

    /** The last day of the round starting [start]. */
    public static LocalDate end(LocalDate start, String period) {
        return plus(start, period, 1).minusDays(1);
    }

    /** At most this many rounds in a row. */
    public static int maxRounds(String period) {
        return switch (of(period)) {
            case DAY -> 90;
            case MONTH -> 12;
            default -> 52;
        };
    }
}
