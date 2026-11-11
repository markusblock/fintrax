package org.fintrax.ui;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.fintrax.service.SettingsService;

import java.util.Optional;

/** Persists and restores usable screen bounds for application windows. */
public final class WindowGeometry {
    public static final String SETTING_KEY = "window.geometry";

    private static final double MIN_WIDTH = 1024;
    private static final double MIN_HEIGHT = 768;
    private static final double MIN_VISIBLE_WIDTH = 80;
    private static final double MIN_VISIBLE_HEIGHT = 40;

    private WindowGeometry() {
    }

    /** Restores the primary application's window bounds. */
    public static void restore(Stage stage, SettingsService settingsService) {
        restore(stage, settingsService, SETTING_KEY, MIN_WIDTH, MIN_HEIGHT);
    }

    /** Restores bounds from a window-specific setting when they meet its minimum dimensions. */
    public static void restore(Stage stage, SettingsService settingsService, String settingKey,
                               double minimumWidth, double minimumHeight) {
        parse(settingsService.getSettingValue(settingKey))
                .filter(bounds -> isUsable(bounds, minimumWidth, minimumHeight))
                .ifPresent(bounds -> {
                    stage.setWidth(bounds.width());
                    stage.setHeight(bounds.height());
                    stage.setX(bounds.x());
                    stage.setY(bounds.y());
                });
    }

    /** Saves the primary application's window bounds. */
    public static void save(Stage stage, SettingsService settingsService) {
        save(stage, settingsService, SETTING_KEY, MIN_WIDTH, MIN_HEIGHT);
    }

    /** Saves a window's bounds under its own setting key when they meet its minimum dimensions. */
    public static void save(Stage stage, SettingsService settingsService, String settingKey,
                            double minimumWidth, double minimumHeight) {
        if (stage.isMaximized() || stage.isFullScreen()) return;

        WindowBounds bounds = new WindowBounds(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        if (isUsable(bounds, minimumWidth, minimumHeight)) {
            settingsService.saveSettingValue(settingKey, bounds.encode());
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

    private static boolean isUsable(WindowBounds bounds, double minimumWidth, double minimumHeight) {
        if (!Double.isFinite(bounds.x()) || !Double.isFinite(bounds.y())
                || !Double.isFinite(bounds.width()) || !Double.isFinite(bounds.height())
                || bounds.width() < minimumWidth || bounds.height() < minimumHeight) {
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
