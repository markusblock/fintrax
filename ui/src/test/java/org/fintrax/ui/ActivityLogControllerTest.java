package org.fintrax.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.DatePicker;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.ActivityAction;
import org.fintrax.model.ActivityLog;
import org.fintrax.model.EntityType;
import org.fintrax.service.SettingsService;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    void activityLogFiltersEntriesByInclusiveDateRange() {
        LocalDate startDate = LocalDate.of(2098, 1, 1);
        long id = System.nanoTime();
        ActivityLog before = activityLog(id, startDate.atTime(23, 59));
        ActivityLog included = activityLog(id + 1, startDate.plusDays(1).atStartOfDay());
        ActivityLog after = activityLog(id + 2, startDate.plusDays(2).atStartOfDay());
        StoreManager store = applicationContext.getBean(StoreManager.class);
        store.getRoot().getActivityLogs().addAll(List.of(before, included, after));
        store.store(store.getRoot().getActivityLogs());

        DatePicker fromDate = lookup("#fromDateFilter").query();
        DatePicker toDate = lookup("#toDateFilter").query();
        interact(() -> {
            fromDate.setValue(startDate.plusDays(1));
            toDate.setValue(startDate.plusDays(1));
        });

        TableView<ActivityLog> table = lookup("#logTable").query();
        assertEquals(List.of(included.getId()), table.getItems().stream().map(ActivityLog::getId).toList());
    }

    private ActivityLog activityLog(long id, LocalDateTime timestamp) {
        return ActivityLog.builder().id(id).timestamp(timestamp).action(ActivityAction.SYNC)
                .entityType(EntityType.ACCOUNT).entityId(1L).description("Date filter test").build();
    }
}
