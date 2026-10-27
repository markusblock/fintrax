package org.fintrax.ui.control;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import org.fintrax.service.SettingsService;

import java.util.List;
import java.util.stream.Collectors;

public final class TableColumnVisibilityMenu {
    private TableColumnVisibilityMenu() {}

    public record Column(String key, TableColumn<?, ?> column) {}

    public static void install(MenuButton menuButton, String settingKey,
                               SettingsService settingsService, List<Column> columns) {
        restoreColumnVisibility(settingKey, settingsService, columns);
        menuButton.getItems().setAll(columns.stream()
                .map(config -> createMenuItem(config, settingKey, settingsService, columns))
                .toList());
    }

    private static void restoreColumnVisibility(String settingKey, SettingsService settingsService,
                                                List<Column> columns) {
        String value = settingsService.getSettingValue(settingKey);
        if (value == null) return;

        for (String part : value.split(",")) {
            String[] entry = part.split("=", 2);
            if (entry.length != 2) continue;
            columns.stream()
                    .filter(config -> config.key().equals(entry[0]))
                    .findFirst()
                    .ifPresent(config -> config.column().setVisible(Boolean.parseBoolean(entry[1])));
        }
    }

    private static CheckMenuItem createMenuItem(Column config, String settingKey,
                                               SettingsService settingsService, List<Column> columns) {
        TableColumn<?, ?> column = config.column();
        CheckMenuItem menuItem = new CheckMenuItem(column.getText());
        menuItem.setSelected(column.isVisible());
        menuItem.selectedProperty().addListener((observable, wasVisible, isVisible) ->
                column.setVisible(isVisible));
        column.visibleProperty().addListener((observable, wasVisible, isVisible) -> {
            menuItem.setSelected(isVisible);
            settingsService.saveSettingValue(settingKey, columns.stream()
                    .map(item -> item.key() + "=" + item.column().isVisible())
                    .collect(Collectors.joining(",")));
        });
        return menuItem;
    }
}
