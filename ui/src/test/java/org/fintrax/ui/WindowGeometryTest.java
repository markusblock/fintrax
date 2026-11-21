package org.fintrax.ui;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.fintrax.service.SettingsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowGeometryTest extends AbstractUITest {
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new StackPane(), 1024, 768));
        stage.show();
    }

    @Test
    void restoresSavedWindowBoundsAndPersistsCurrentBounds() {
        SettingsService settingsService = applicationContext.getBean(SettingsService.class);
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double savedX = screen.getMinX() + 50;
        double savedY = screen.getMinY() + 60;

        interact(() -> {
            stage.setMaximized(false);
            stage.setFullScreen(false);
            stage.setX(savedX);
            stage.setY(savedY);
            stage.setWidth(1100);
            stage.setHeight(800);
            WindowGeometry.save(stage, settingsService);

            stage.setX(screen.getMinX() + 100);
            stage.setY(screen.getMinY() + 100);
            stage.setWidth(1024);
            stage.setHeight(768);
            WindowGeometry.restore(stage, settingsService);
        });

        assertEquals(savedX, stage.getX(), 1);
        assertEquals(savedY, stage.getY(), 1);
        assertEquals(1100, stage.getWidth(), 1);
        assertEquals(800, stage.getHeight(), 1);
    }

    @Test
    void ignoresSavedBoundsThatDoNotMeetMinimumWindowSize() {
        SettingsService settingsService = applicationContext.getBean(SettingsService.class);
        interact(() -> {
            stage.setX(40);
            stage.setY(45);
            stage.setWidth(1024);
            stage.setHeight(768);
            settingsService.saveSettingValue(WindowGeometry.SETTING_KEY, "10,20,800,600");

            WindowGeometry.restore(stage, settingsService);
        });

        assertEquals(40, stage.getX(), 1);
        assertEquals(45, stage.getY(), 1);
        assertEquals(1024, stage.getWidth(), 1);
        assertEquals(768, stage.getHeight(), 1);
    }
}
