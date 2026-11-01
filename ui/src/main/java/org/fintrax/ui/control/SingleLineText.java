package org.fintrax.ui.control;

public final class SingleLineText {
    private SingleLineText() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isWhitespace(codePoint)) {
                pendingSpace = normalized.length() > 0;
                continue;
            }
            if (pendingSpace) {
                normalized.append(' ');
            }
            normalized.appendCodePoint(codePoint);
            pendingSpace = false;
        }
        return normalized.toString();
    }

    private static boolean isWhitespace(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isWhitespace(codePoint)
                || codePoint == 0x85
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
