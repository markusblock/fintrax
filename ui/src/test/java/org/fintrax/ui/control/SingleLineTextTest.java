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
    void nullAndWhitespaceOnlyTextBecomeEmpty() {
        assertEquals("", SingleLineText.normalize(null));
        assertEquals("", SingleLineText.normalize(" \t\n "));
    }
}
