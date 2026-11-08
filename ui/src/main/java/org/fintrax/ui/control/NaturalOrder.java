package org.fintrax.ui.control;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/** Human-friendly ordering: compare digit runs numerically and text using the active locale. */
public final class NaturalOrder {
    private NaturalOrder() {}

    public static Comparator<String> comparator(Locale locale) {
        return (left, right) -> compare(left, right, locale);
    }

    private static int compare(String left, String right, Locale locale) {
        String first = Objects.requireNonNullElse(left, "");
        String second = Objects.requireNonNullElse(right, "");
        Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.SECONDARY);

        int firstIndex = 0;
        int secondIndex = 0;
        while (firstIndex < first.length() && secondIndex < second.length()) {
            boolean firstDigit = Character.isDigit(first.charAt(firstIndex));
            boolean secondDigit = Character.isDigit(second.charAt(secondIndex));
            int firstEnd = segmentEnd(first, firstIndex, firstDigit);
            int secondEnd = segmentEnd(second, secondIndex, secondDigit);
            String firstPart = first.substring(firstIndex, firstEnd);
            String secondPart = second.substring(secondIndex, secondEnd);

            int comparison = firstDigit && secondDigit
                    ? compareDigits(firstPart, secondPart)
                    : collator.compare(firstPart, secondPart);
            if (comparison != 0) return comparison;
            firstIndex = firstEnd;
            secondIndex = secondEnd;
        }

        int remainingComparison = Integer.compare(first.length() - firstIndex, second.length() - secondIndex);
        return remainingComparison != 0 ? remainingComparison : first.compareTo(second);
    }

    private static int segmentEnd(String value, int start, boolean digits) {
        int end = start + 1;
        while (end < value.length() && Character.isDigit(value.charAt(end)) == digits) end++;
        return end;
    }

    private static int compareDigits(String first, String second) {
        int firstSignificant = skipLeadingZeroes(first);
        int secondSignificant = skipLeadingZeroes(second);
        int significantLength = Integer.compare(first.length() - firstSignificant,
                second.length() - secondSignificant);
        if (significantLength != 0) return significantLength;

        for (int index = 0; index < first.length() - firstSignificant; index++) {
            int comparison = Integer.compare(Character.digit(first.charAt(firstSignificant + index), 10),
                    Character.digit(second.charAt(secondSignificant + index), 10));
            if (comparison != 0) return comparison;
        }
        return Integer.compare(first.length(), second.length());
    }

    private static int skipLeadingZeroes(String value) {
        int index = 0;
        while (index < value.length() - 1 && Character.digit(value.charAt(index), 10) == 0) index++;
        return index;
    }
}
