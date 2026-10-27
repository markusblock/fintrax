package org.fintrax.ui.controller;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionDateFiltersTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 3, 13);

    @Test
    void previousCalendarYearCoversWholePriorYear() {
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.PREVIOUS_YEAR,
                        TODAY, Locale.US));
    }

    @Test
    void previousCalendarMonthCoversWholePriorMonth() {
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.PREVIOUS_MONTH,
                        TODAY, Locale.US));
    }

    @Test
    void previousCalendarWeekUsesLocaleWeekStart() {
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 3, 3), LocalDate.of(2024, 3, 9)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.PREVIOUS_WEEK,
                        TODAY, Locale.US));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 3, 4), LocalDate.of(2024, 3, 10)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.PREVIOUS_WEEK,
                        TODAY, Locale.GERMANY));
    }

    @Test
    void rollingPresetsIncludeTodayAndRequestedNumberOfDays() {
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 3, 7), TODAY),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.LAST_7_DAYS,
                        TODAY, Locale.US));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 2, 15), TODAY),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.LAST_4_WEEKS,
                        TODAY, Locale.US));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2023, 3, 13), TODAY),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.LAST_12_MONTHS,
                        TODAY, Locale.US));
    }

    @Test
    void allTimePresetHasNoDateBounds() {
        assertEquals(new TransactionDateFilters.Range(null, null),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.ALL_TIME,
                        TODAY, Locale.US));
    }

    @Test
    void dateTextUsesSelectedLocale() {
        assertEquals(LocalDate.of(2024, 3, 8),
                TransactionDateFilters.parseDate("08.03.24", Locale.GERMANY));
        assertEquals(LocalDate.of(2024, 3, 8),
                TransactionDateFilters.parseDate("3/8/24", Locale.US));
        for (Locale locale : List.of(Locale.forLanguageTag("en"), Locale.forLanguageTag("de"))) {
            String formatted = TransactionDateFilters.formatter(locale)
                    .format(LocalDate.of(2024, 3, 8));
            assertEquals(LocalDate.of(2024, 3, 8), TransactionDateFilters.parseDate(formatted, locale));
        }
        assertNull(TransactionDateFilters.parseDate("  ", Locale.US));
    }

    @Test
    void invalidDateTextIsRejected() {
        assertThrows(DateTimeParseException.class,
                () -> TransactionDateFilters.parseDate("not a date", Locale.US));
        assertThrows(DateTimeParseException.class,
                () -> TransactionDateFilters.parseDate("2/30/24", Locale.US));
    }
}
