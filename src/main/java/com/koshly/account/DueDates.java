package com.koshly.account;

import java.time.LocalDate;
import java.time.YearMonth;

/** Works out the next due date from a day of the month (same rule as the app). */
final class DueDates {

    private DueDates() {
    }

    /**
     * Next date, today or later, that falls on {@code day}. Short months use
     * their last day: day 31 in February becomes Feb 28 (or 29).
     */
    static LocalDate next(int day, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        LocalDate candidate = month.atDay(Math.min(day, month.lengthOfMonth()));
        if (candidate.isBefore(today)) {
            YearMonth nextMonth = month.plusMonths(1);
            candidate = nextMonth.atDay(Math.min(day, nextMonth.lengthOfMonth()));
        }
        return candidate;
    }
}
