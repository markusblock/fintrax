package org.fintrax.ui.control;

public final class SingleLineText {
    private SingleLineText() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("(?:\\R|\\t)+", " ")
                .replaceAll(" {2,}", " ")
                .strip();
    }
}
