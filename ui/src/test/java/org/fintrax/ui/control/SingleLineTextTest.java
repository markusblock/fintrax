package org.fintrax.ui.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleLineTextTest {
    @Test
    void replacesTabsAndLineBreaksWithSingleSpaces() {
        assertEquals("First line second line third line",
                SingleLineText.normalize("First line\n\tsecond line\r\nthird line"));
    }

    @Test
    void replacesUnicodeLineBreaksAndCollapsesWhitespace() {
        assertEquals("First second third",
                SingleLineText.normalize(" First  \u2028second\u0085\tthird "));
    }

    @Test
    void nullAndWhitespaceOnlyTextBecomeEmpty() {
        assertEquals("", SingleLineText.normalize(null));
        assertEquals("", SingleLineText.normalize(" \t\n "));
    }
}
