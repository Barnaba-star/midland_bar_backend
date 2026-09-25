package com.midland.bar.Bar.Projection;

import java.time.LocalDate;

/** One point on the branch's revenue line: a day, and what it took. */
public interface RevenueTrendProjection {
    LocalDate getDate();
    Long getAmount();
}
