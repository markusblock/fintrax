package org.fintrax.ui.controller;

import java.time.LocalDate;
import java.time.chrono.IsoEra;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Objects;

final class TransactionDateFilters {
    private TransactionDateFilters() {
    }

    enum Preset {
        ALL_TIME,
        THIS_MONTH,
        PREVIOUS_MONTH,
        TWO_MONTHS_AGO,
        THIS_QUARTER,
        PREVIOUS_QUARTER,
        TWO_QUARTERS_AGO,
        THIS_YEAR,
        PREVIOUS_YEAR,
        TWO_YEARS_AGO,
        LAST_7_DAYS,
        LAST_30_DAYS,
        LAST_12_MONTHS,
        CUSTOM
    }

    record Range(LocalDate fromDate, LocalDate toDate) {
    }

    /**
     * Calendar presets cover their full calendar period. The 7- and 30-day presets include exactly
     * that many dates ending today; the 12-month preset starts on the same date one year earlier.
     * All returned bounds are inclusive.
     */
    static Range rangeFor(Preset preset, LocalDate today) {
        Objects.requireNonNull(preset);
        Objects.requireNonNull(today);

        return switch (preset) {
            case ALL_TIME -> new Range(null, null);
            case THIS_MONTH -> calendarMonth(today, 0);
            case PREVIOUS_MONTH -> calendarMonth(today, 1);
            case TWO_MONTHS_AGO -> calendarMonth(today, 2);
            case THIS_QUARTER -> calendarQuarter(today, 0);
            case PREVIOUS_QUARTER -> calendarQuarter(today, 1);
            case TWO_QUARTERS_AGO -> calendarQuarter(today, 2);
            case THIS_YEAR -> calendarYear(today, 0);
            case PREVIOUS_YEAR -> calendarYear(today, 1);
            case TWO_YEARS_AGO -> calendarYear(today, 2);
            case LAST_7_DAYS -> new Range(today.minusDays(6), today);
            case LAST_30_DAYS -> new Range(today.minusDays(29), today);
            case LAST_12_MONTHS -> new Range(today.minusMonths(12), today);
            case CUSTOM -> throw new IllegalArgumentException("Custom date range has no preset bounds");
        };
    }

    private static Range calendarMonth(LocalDate today, int monthsAgo) {
        LocalDate start = today.withDayOfMonth(1).minusMonths(monthsAgo);
        return new Range(start, start.plusMonths(1).minusDays(1));
    }

    private static Range calendarQuarter(LocalDate today, int quartersAgo) {
        int firstMonthOfQuarter = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate start = LocalDate.of(today.getYear(), firstMonthOfQuarter, 1)
                .minusMonths(3L * quartersAgo);
        return new Range(start, start.plusMonths(3).minusDays(1));
    }

    private static Range calendarYear(LocalDate today, int yearsAgo) {
        int year = today.getYear() - yearsAgo;
        return new Range(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    static DateTimeFormatter formatter(Locale locale) {
        return new DateTimeFormatterBuilder()
                .appendLocalized(FormatStyle.SHORT, null)
                .parseDefaulting(ChronoField.ERA, IsoEra.CE.getValue())
                .toFormatter(locale)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    static LocalDate parseDate(String text, Locale locale) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text.trim(), formatter(locale));
    }

}
