package org.fintrax.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.ActivityLog;
import org.fintrax.service.SettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityLogControllerTest extends AbstractUITest {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("activityLog");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void activityLogColumnMenuPersistsVisibilityAndOrder() {
        TableView<ActivityLog> table = lookup("#logTable").query();
        MenuButton columnMenu = lookup("#columnMenuButton").query();

        assertSame(columnMenu, table.getColumns().getLast().getGraphic());
        assertEquals(List.of("col.timestamp", "col.action", "col.entityType", "col.entityId",
                        "col.description").stream().map(I18n::get).toList(),
                columnMenu.getItems().stream().map(MenuItem::getText).toList());

        var originalColumnOrder = List.copyOf(table.getColumns());
        CheckMenuItem descriptionItem = (CheckMenuItem) columnMenu.getItems().get(4);
        try {
            interact(() -> descriptionItem.setSelected(false));
            assertFalse(table.getColumns().get(4).isVisible());
            assertTrue(applicationContext.getBean(SettingsService.class)
                    .getSettingValue("activity.log.columns").contains("description=false"));

            interact(() -> {
                var timestampColumn = table.getColumns().remove(0);
                table.getColumns().add(1, timestampColumn);
            });
            assertEquals("action,timestamp,entityType,entityId,description",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("activity.log.columns.order"));
        } finally {
            interact(() -> {
                descriptionItem.setSelected(true);
                table.getColumns().setAll(originalColumnOrder);
            });
        }
    }
}
