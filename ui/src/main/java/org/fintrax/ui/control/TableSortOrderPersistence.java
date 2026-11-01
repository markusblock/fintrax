package org.fintrax.ui.control;

import javafx.collections.ListChangeListener;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.fintrax.service.SettingsService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class TableSortOrderPersistence {
    private TableSortOrderPersistence() {}

    public static <S> void install(TableView<S> table, String settingKey,
                                   SettingsService settingsService, List<TableColumnConfig> columns) {
        Map<String, TableColumn<?, ?>> columnsByKey = columns.stream()
                .collect(Collectors.toMap(TableColumnConfig::key, TableColumnConfig::column));
        restoreSortOrder(table, settingsService.getSettingValue(settingKey), columnsByKey);

        table.getSortOrder().addListener((ListChangeListener<TableColumn<S, ?>>) change ->
                saveSortOrder(table, settingKey, settingsService, columns));
        columns.forEach(config -> config.column().sortTypeProperty().addListener(
                (observable, oldType, newType) -> saveSortOrder(table, settingKey, settingsService, columns)));
    }

    private static <S> void restoreSortOrder(TableView<S> table, String value,
                                             Map<String, TableColumn<?, ?>> columnsByKey) {
        if (value == null || value.isBlank()) return;

        List<TableColumn<S, ?>> sortOrder = new ArrayList<>();
        Set<TableColumn<?, ?>> restoredColumns = new HashSet<>();
        for (String entry : value.split(",")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;
            TableColumn<?, ?> column = columnsByKey.get(parts[0]);
            if (column == null || !column.isSortable() || restoredColumns.contains(column)) continue;

            try {
                TableColumn.SortType sortType = TableColumn.SortType.valueOf(parts[1]);
                restoredColumns.add(column);
                column.setSortType(sortType);
                sortOrder.add(asTableColumn(column));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale or malformed preference values.
            }
        }
        table.getSortOrder().setAll(sortOrder);
    }

    private static <S> void saveSortOrder(TableView<S> table, String settingKey,
                                          SettingsService settingsService, List<TableColumnConfig> columns) {
        Map<TableColumn<?, ?>, String> keysByColumn = new HashMap<>();
        columns.forEach(config -> keysByColumn.put(config.column(), config.key()));
        String value = table.getSortOrder().stream()
                .map(column -> {
                    String key = keysByColumn.get(column);
                    return key == null ? null : key + "=" + column.getSortType().name();
                })
                .filter(entry -> entry != null)
                .collect(Collectors.joining(","));
        settingsService.saveSettingValue(settingKey, value);
    }

    @SuppressWarnings("unchecked")
    private static <S> TableColumn<S, ?> asTableColumn(TableColumn<?, ?> column) {
        return (TableColumn<S, ?>) column;
    }
}
