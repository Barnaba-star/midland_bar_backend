package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Model.PriceRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** When a happy hour runs - the day it is on and times past midnight. No database. */
class PriceRuleRunsTest {

    private static PriceRule rule(String days, String from, String to) {
        PriceRule r = new PriceRule();
        r.setDays(days);
        r.setStartTime(LocalTime.parse(from));
        r.setEndTime(LocalTime.parse(to));
        return r;
    }

    // 2026-10-19 is a Monday (1).
    private static LocalDateTime mon(String t) { return LocalDateTime.parse("2026-10-19T" + t); }

    @Test
    void sameDayWindow() {
        PriceRule r = rule("12345", "17:00", "19:00");
        assertTrue(PriceRuleService.runs(r, mon("17:00")));
        assertTrue(PriceRuleService.runs(r, mon("18:59")));
        assertFalse(PriceRuleService.runs(r, mon("19:00")));
        assertFalse(PriceRuleService.runs(r, mon("16:59")));
        assertFalse(PriceRuleService.runs(rule("67", "17:00", "19:00"), mon("18:00")), "not on Monday");
    }

    @Test
    void pastMidnightBelongsToTheDayItStarted() {
        PriceRule fri = rule("5", "22:00", "02:00");
        assertTrue(PriceRuleService.runs(fri, LocalDateTime.parse("2026-10-23T23:30")), "Friday night");
        assertTrue(PriceRuleService.runs(fri, LocalDateTime.parse("2026-10-24T01:30")), "early Saturday is still Friday's");
        assertFalse(PriceRuleService.runs(fri, LocalDateTime.parse("2026-10-24T23:30")), "Saturday night is not");
        assertFalse(PriceRuleService.runs(fri, LocalDateTime.parse("2026-10-24T02:00")));
    }

    @Test
    void buyFiveGetTwo() {
        // Once per bill: 6 -> the 6th free, 7 and 10 -> 2 free, 20 -> still 2.
        assertEquals(0, PriceRuleService.freeEarned(5, 5, 2, true));
        assertEquals(1, PriceRuleService.freeEarned(6, 5, 2, true));
        assertEquals(2, PriceRuleService.freeEarned(7, 5, 2, true));
        assertEquals(2, PriceRuleService.freeEarned(10, 5, 2, true));
        assertEquals(2, PriceRuleService.freeEarned(20, 5, 2, true));
        // Every time: again after each 7 - 10 -> 2, 13 -> 3, 14 -> 4, 20 -> 5 (6-7, 13-14, 20).
        assertEquals(2, PriceRuleService.freeEarned(10, 5, 2, false));
        assertEquals(3, PriceRuleService.freeEarned(13, 5, 2, false));
        assertEquals(4, PriceRuleService.freeEarned(14, 5, 2, false));
        assertEquals(5, PriceRuleService.freeEarned(20, 5, 2, false));
    }
}
