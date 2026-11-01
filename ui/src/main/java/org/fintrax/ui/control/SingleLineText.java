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
        var codePoints = text.codePoints().iterator();
        while (codePoints.hasNext()) {
            int codePoint = codePoints.nextInt();
            if (isWhitespace(codePoint)) {
                pendingSpace = !normalized.isEmpty();
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
