package com.banking.models;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.Instant;

class ReportPeriodTest {
    @Test void selectedDayIncludesFullUtcDateButExcludesNextMidnight() {
        ReportPeriod today = new ReportPeriod(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17));
        assertEquals(Instant.parse("2026-11-17T00:00:00Z"), today.startInclusive());
        assertEquals(Instant.parse("2026-11-18T00:00:00Z"), today.endExclusive());
    }

    @Test void selectedRangeIsInclusiveOfEndDate() {
        ReportPeriod week = new ReportPeriod(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17));
        assertEquals(Instant.parse("2026-11-10T00:00:00Z"), week.startInclusive());
        assertEquals(Instant.parse("2026-11-18T00:00:00Z"), week.endExclusive());
    }

    @Test void invalidRangesFailFast() {
        assertThrows(IllegalArgumentException.class, () ->
            new ReportPeriod(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 17)));
        assertThrows(IllegalArgumentException.class, () ->
            new ReportPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 11, 17)));
        assertThrows(NullPointerException.class, () ->
            new ReportPeriod(null, LocalDate.of(2026, 11, 17)));
    }
}
