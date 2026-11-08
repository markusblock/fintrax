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
    void yearPresetsCoverThisPreviousAndSecondPreviousCalendarYears() {
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.THIS_YEAR, TODAY));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.PREVIOUS_YEAR,
                        TODAY));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.TWO_YEARS_AGO, TODAY));
    }

    @Test
    void monthPresetsCoverThisPreviousAndSecondPreviousCalendarMonths() {
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.THIS_MONTH, TODAY));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.PREVIOUS_MONTH,
                        TODAY));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.TWO_MONTHS_AGO, TODAY));
    }

    @Test
    void quarterPresetsCoverThisPreviousAndSecondPreviousCalendarQuarters() {
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.THIS_QUARTER, TODAY));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2023, 10, 1), LocalDate.of(2023, 12, 31)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.PREVIOUS_QUARTER, TODAY));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2023, 7, 1), LocalDate.of(2023, 9, 30)),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.TWO_QUARTERS_AGO, TODAY));
    }

    @Test
    void rollingPresetsIncludeTodayAndRequestedDuration() {
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 3, 7), TODAY),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.LAST_7_DAYS,
                        TODAY));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2024, 2, 13), TODAY),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.LAST_30_DAYS,
                        TODAY));
        assertEquals(new TransactionDateFilters.Range(
                        LocalDate.of(2023, 3, 13), TODAY),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.LAST_12_MONTHS,
                        TODAY));
    }

    @Test
    void allTimePresetHasNoDateBounds() {
        assertEquals(new TransactionDateFilters.Range(null, null),
                TransactionDateFilters.rangeFor(TransactionDateFilters.Preset.ALL_TIME,
                        TODAY));
    }

    @Test
    void presetsStayCompactAndGroupedByCalendarPeriod() {
        assertEquals(List.of(
                        TransactionDateFilters.Preset.LAST_7_DAYS,
                        TransactionDateFilters.Preset.LAST_30_DAYS,
                        TransactionDateFilters.Preset.LAST_12_MONTHS,
                        TransactionDateFilters.Preset.THIS_MONTH,
                        TransactionDateFilters.Preset.PREVIOUS_MONTH,
                        TransactionDateFilters.Preset.TWO_MONTHS_AGO,
                        TransactionDateFilters.Preset.THIS_QUARTER,
                        TransactionDateFilters.Preset.PREVIOUS_QUARTER,
                        TransactionDateFilters.Preset.TWO_QUARTERS_AGO,
                        TransactionDateFilters.Preset.THIS_YEAR,
                        TransactionDateFilters.Preset.PREVIOUS_YEAR,
                        TransactionDateFilters.Preset.TWO_YEARS_AGO,
                        TransactionDateFilters.Preset.ALL_TIME,
                        TransactionDateFilters.Preset.CUSTOM),
                List.of(TransactionDateFilters.Preset.values()));
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
