package org.fintrax.ui.control;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.fintrax.service.SettingsService;

import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TableColumnVisibilityMenu {
    private TableColumnVisibilityMenu() {}

    public record Column(String key, TableColumn<?, ?> column) {}

    public static <S> void install(TableView<S> table, MenuButton menuButton, String settingKey,
                               SettingsService settingsService, List<Column> columns) {
        restoreColumnVisibility(settingKey, settingsService, columns);
        restoreColumnOrder(table, settingKey, settingsService, columns);
        menuButton.getItems().setAll(columns.stream()
                .map(config -> createMenuItem(config, settingKey, settingsService, columns))
                .toList());
        table.getColumns().addListener((javafx.collections.ListChangeListener<TableColumn<S, ?>>) change ->
                saveColumnOrder(table, settingKey, settingsService, columns));
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

    private static <S> void restoreColumnOrder(TableView<S> table, String settingKey,
                                               SettingsService settingsService, List<Column> columns) {
        String value = settingsService.getSettingValue(settingKey + ".order");
        if (value == null || value.isBlank()) return;

        Map<String, Integer> orderByKey = new HashMap<>();
        for (String key : value.split(",")) {
            if (columns.stream().anyMatch(column -> column.key().equals(key))) {
                orderByKey.putIfAbsent(key, orderByKey.size());
            }
        }
        columns.forEach(column -> orderByKey.putIfAbsent(column.key(), orderByKey.size()));

        Map<TableColumn<?, ?>, Integer> orderByColumn = new IdentityHashMap<>();
        columns.forEach(column -> orderByColumn.put(column.column(), orderByKey.get(column.key())));
        int afterConfiguredColumns = orderByColumn.size();
        table.getColumns().sort(Comparator.comparingInt(
                column -> orderByColumn.getOrDefault(column, afterConfiguredColumns)));
    }

    private static <S> void saveColumnOrder(TableView<S> table, String settingKey,
                                            SettingsService settingsService, List<Column> columns) {
        String value = table.getColumns().stream()
                .map(tableColumn -> columns.stream()
                        .filter(config -> config.column() == tableColumn)
                        .map(Column::key)
                        .findFirst()
                        .orElse(null))
                .filter(key -> key != null)
                .collect(Collectors.joining(","));
        settingsService.saveSettingValue(settingKey + ".order", value);
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
