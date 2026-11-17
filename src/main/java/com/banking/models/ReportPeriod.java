package com.banking.models;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Inclusive UTC calendar dates, queried as [start, end + 1 day). */
public record ReportPeriod(LocalDate startDate, LocalDate endDate) {
    public ReportPeriod {
        Objects.requireNonNull(startDate, "Start date is required.");
        Objects.requireNonNull(endDate, "End date is required.");
        if (endDate.isBefore(startDate))
            throw new IllegalArgumentException("End date must be on or after start date.");
        if (ChronoUnit.DAYS.between(startDate, endDate) > 366)
            throw new IllegalArgumentException("Select a date range of at most 367 calendar days.");
    }

    public static ReportPeriod todayUtc() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return new ReportPeriod(today, today);
    }

    public Instant startInclusive() {
        return startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public Instant endExclusive() {
        return endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
