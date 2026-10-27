package org.fintrax.ui.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.chrono.IsoEra;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Objects;

final class TransactionDateFilters {
    private TransactionDateFilters() {
    }

    enum Preset {
        ALL_TIME,
        PREVIOUS_WEEK,
        PREVIOUS_MONTH,
        PREVIOUS_YEAR,
        LAST_12_MONTHS,
        LAST_4_WEEKS,
        LAST_7_DAYS,
        CUSTOM
    }

    record Range(LocalDate fromDate, LocalDate toDate) {
    }

    static Range rangeFor(Preset preset, LocalDate today, Locale locale) {
        Objects.requireNonNull(preset);
        Objects.requireNonNull(today);
        Objects.requireNonNull(locale);

        return switch (preset) {
            case ALL_TIME -> new Range(null, null);
            case PREVIOUS_WEEK -> previousWeek(today, locale);
            case PREVIOUS_MONTH -> new Range(
                    today.withDayOfMonth(1).minusMonths(1),
                    today.withDayOfMonth(1).minusDays(1));
            case PREVIOUS_YEAR -> new Range(
                    LocalDate.of(today.getYear() - 1, 1, 1),
                    LocalDate.of(today.getYear() - 1, 12, 31));
            case LAST_12_MONTHS -> new Range(today.minusMonths(12), today);
            case LAST_4_WEEKS -> new Range(today.minusDays(27), today);
            case LAST_7_DAYS -> new Range(today.minusDays(6), today);
            case CUSTOM -> throw new IllegalArgumentException("Custom date range has no preset bounds");
        };
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

    private static Range previousWeek(LocalDate today, Locale locale) {
        DayOfWeek firstDay = WeekFields.of(locale).getFirstDayOfWeek();
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDay));
        LocalDate previousWeekStart = currentWeekStart.minusWeeks(1);
        return new Range(previousWeekStart, currentWeekStart.minusDays(1));
    }
}
