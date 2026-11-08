package org.fintrax.ui;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
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

        interact(() -> {
            stage.setX(50);
            stage.setY(60);
            stage.setWidth(1100);
            stage.setHeight(800);
            WindowGeometry.save(stage, settingsService);

            stage.setX(100);
            stage.setY(100);
            stage.setWidth(1024);
            stage.setHeight(768);
            WindowGeometry.restore(stage, settingsService);
        });

        assertEquals(50, stage.getX(), 1);
        assertEquals(60, stage.getY(), 1);
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
