package org.fintrax.ui;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.fintrax.service.SettingsService;

import java.util.Optional;

final class WindowGeometry {
    static final String SETTING_KEY = "window.geometry";

    private static final double MIN_WIDTH = 1024;
    private static final double MIN_HEIGHT = 768;
    private static final double MIN_VISIBLE_WIDTH = 80;
    private static final double MIN_VISIBLE_HEIGHT = 40;

    private WindowGeometry() {
    }

    static void restore(Stage stage, SettingsService settingsService) {
        parse(settingsService.getSettingValue(SETTING_KEY))
                .filter(WindowGeometry::isUsable)
                .ifPresent(bounds -> {
                    stage.setWidth(bounds.width());
                    stage.setHeight(bounds.height());
                    stage.setX(bounds.x());
                    stage.setY(bounds.y());
                });
    }

    static void save(Stage stage, SettingsService settingsService) {
        if (stage.isMaximized() || stage.isFullScreen()) return;

        WindowBounds bounds = new WindowBounds(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        if (isUsable(bounds)) {
            settingsService.saveSettingValue(SETTING_KEY, bounds.encode());
        }
    }

    private static Optional<WindowBounds> parse(String value) {
        if (value == null) return Optional.empty();
        String[] parts = value.split(",", -1);
        if (parts.length != 4) return Optional.empty();
        try {
            return Optional.of(new WindowBounds(
                    Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean isUsable(WindowBounds bounds) {
        if (!Double.isFinite(bounds.x()) || !Double.isFinite(bounds.y())
                || !Double.isFinite(bounds.width()) || !Double.isFinite(bounds.height())
                || bounds.width() < MIN_WIDTH || bounds.height() < MIN_HEIGHT) {
            return false;
        }

        return Screen.getScreens().stream()
                .map(Screen::getVisualBounds)
                .anyMatch(screen -> visibleWidth(bounds, screen) >= MIN_VISIBLE_WIDTH
                        && visibleHeight(bounds, screen) >= MIN_VISIBLE_HEIGHT);
    }

    private static double visibleWidth(WindowBounds bounds, Rectangle2D screen) {
        return Math.min(bounds.x() + bounds.width(), screen.getMaxX())
                - Math.max(bounds.x(), screen.getMinX());
    }

    private static double visibleHeight(WindowBounds bounds, Rectangle2D screen) {
        return Math.min(bounds.y() + bounds.height(), screen.getMaxY())
                - Math.max(bounds.y(), screen.getMinY());
    }

    private record WindowBounds(double x, double y, double width, double height) {
        private String encode() {
            return String.join(",", Double.toString(x), Double.toString(y),
                    Double.toString(width), Double.toString(height));
        }
    }
}
