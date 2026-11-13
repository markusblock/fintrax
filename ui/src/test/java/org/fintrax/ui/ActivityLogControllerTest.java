package org.fintrax.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.ActivityLog;
import org.fintrax.service.SettingsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
                columnMenu.getItems().stream().filter(CheckMenuItem.class::isInstance)
                        .map(MenuItem::getText).toList());
        assertEquals(I18n.get("button.columns.reset"), columnMenu.getItems().getLast().getText());

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

    @Test
    void timestampSortDefaultsToNewestAndPersistsAcrossViewReload() throws Exception {
        String sortSettingKey = "activity.log.columns.sort";
        SettingsService settingsService = applicationContext.getBean(SettingsService.class);
        String previousSortOrder = settingsService.getSettingValue(sortSettingKey);
        LocalDateTime olderTimestamp = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime newerTimestamp = LocalDateTime.of(2025, 1, 2, 10, 0);
        ActivityLog olderLog = ActivityLog.builder().id(1L).timestamp(olderTimestamp).build();
        ActivityLog newerLog = ActivityLog.builder().id(2L).timestamp(newerTimestamp).build();

        try {
            TableView<ActivityLog> initialTable = lookup("#logTable").query();
            Stage stage = (Stage) initialTable.getScene().getWindow();
            settingsService.saveSettingValue(sortSettingKey, "");
            Parent defaultView = applicationContext.getBean(ViewLoader.class).load("activityLog");
            interact(() -> stage.getScene().setRoot(defaultView));

            TableView<ActivityLog> table = lookup("#logTable").query();
            assertEquals(1, table.getSortOrder().size());
            assertEquals(I18n.get("col.timestamp"), table.getSortOrder().getFirst().getText());
            assertEquals(TableColumn.SortType.DESCENDING, table.getSortOrder().getFirst().getSortType());
            assertEquals("timestamp=DESCENDING", settingsService.getSettingValue(sortSettingKey));

            interact(() -> {
                table.getItems().setAll(olderLog, newerLog);
                table.sort();
            });
            assertEquals(List.of(newerTimestamp, olderTimestamp),
                    table.getItems().stream().map(ActivityLog::getTimestamp).toList());

            interact(() -> {
                table.getSortOrder().getFirst().setSortType(TableColumn.SortType.ASCENDING);
                table.sort();
            });
            assertEquals("timestamp=ASCENDING", settingsService.getSettingValue(sortSettingKey));

            Parent restoredView = applicationContext.getBean(ViewLoader.class).load("activityLog");
            interact(() -> stage.getScene().setRoot(restoredView));

            TableView<ActivityLog> restoredTable = lookup("#logTable").query();
            assertEquals(1, restoredTable.getSortOrder().size());
            assertEquals(I18n.get("col.timestamp"), restoredTable.getSortOrder().getFirst().getText());
            assertEquals(TableColumn.SortType.ASCENDING, restoredTable.getSortOrder().getFirst().getSortType());
            interact(() -> {
                restoredTable.getItems().setAll(newerLog, olderLog);
                restoredTable.sort();
            });
            assertEquals(List.of(olderTimestamp, newerTimestamp),
                    restoredTable.getItems().stream().map(ActivityLog::getTimestamp).toList());

            MenuButton menuButton = lookup("#columnMenuButton").query();
            interact(menuButton.getItems().getLast()::fire);
            assertEquals(I18n.get("col.timestamp"), restoredTable.getSortOrder().getFirst().getText());
            assertEquals(TableColumn.SortType.DESCENDING, restoredTable.getSortOrder().getFirst().getSortType());
            assertEquals("timestamp=DESCENDING", settingsService.getSettingValue(sortSettingKey));
            assertEquals(List.of(newerTimestamp, olderTimestamp),
                    restoredTable.getItems().stream().map(ActivityLog::getTimestamp).toList());
        } finally {
            settingsService.saveSettingValue(sortSettingKey, previousSortOrder == null ? "" : previousSortOrder);
        }
    }
}
