package com.koshly.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DueDatesTest {

    @Test
    void laterThisMonth() {
        assertThat(DueDates.next(25, LocalDate.of(2026, 11, 20))).isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    void todayCountsAsDue() {
        assertThat(DueDates.next(20, LocalDate.of(2026, 11, 20))).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void alreadyPassedMovesToNextMonth() {
        assertThat(DueDates.next(5, LocalDate.of(2026, 12, 20))).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void shortMonthUsesItsLastDay() {
        assertThat(DueDates.next(31, LocalDate.of(2027, 2, 10))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(DueDates.next(31, LocalDate.of(2028, 2, 10))).isEqualTo(LocalDate.of(2028, 2, 29));
    }
}
