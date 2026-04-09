package com.billme.report.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

public class DateRangeUtils {

    @Getter
    @AllArgsConstructor
    public static class DateRange {
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;
        private final String description;
    }

    public static DateRange resolveRange(String range, LocalDateTime startOverride, LocalDateTime endOverride) {
        // 1. Strict Precedence: If either override is provided, use them and ignore range.
        if (startOverride != null || endOverride != null) {
            LocalDateTime end = endOverride != null ? endOverride : LocalDateTime.now();
            // If start is overridden, use it; if only end is overridden, default to 1 year back for P&L/Statement logic
            LocalDateTime start = startOverride != null ? startOverride : end.minusYears(1);
            return new DateRange(start, end, "custom-range");
        }

        // 2. Resolve via range (Internal Source of Truth)
        LocalDateTime end = LocalDateTime.now();
        String resolvedRange = (range != null) ? range.toLowerCase() : "daily";
        LocalDateTime start;
        String description;

        switch (resolvedRange) {
            case "weekly":
                start = end.minusWeeks(12);
                description = "last-12-weeks";
                break;
            case "monthly":
                start = end.minusMonths(12);
                description = "last-12-months";
                break;
            case "yearly":
                start = end.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                description = "year-to-date";
                break;
            case "quarterly":
                start = end.minusMonths(12);
                description = "last-4-quarters";
                break;
            case "daily":
            default:
                start = end.minusDays(30);
                description = "last-30-days";
                break;
        }

        return new DateRange(start, end, description);
    }
}
