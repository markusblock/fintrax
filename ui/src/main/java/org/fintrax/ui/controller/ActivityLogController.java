package org.fintrax.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.ServiceRegistry;
import org.fintrax.store.StoreManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
public class ActivityLogController {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    private BorderPane rootPane;
    @FXML
    private TableView<ActivityLog> logTable;
    @FXML
    private TableColumn<ActivityLog, LocalDateTime> timestampColumn;
    @FXML
    private TableColumn<ActivityLog, String> actionColumn;
    @FXML
    private TableColumn<ActivityLog, String> entityTypeColumn;
    @FXML
    private TableColumn<ActivityLog, String> entityIdColumn;
    @FXML
    private TableColumn<ActivityLog, String> descriptionColumn;
    @FXML
    private ComboBox<String> actionFilter;
    @FXML
    private ComboBox<String> entityTypeFilter;

    private final StoreManager storeManager = ServiceRegistry.getInstance().getStoreManager();
    private final ObservableList<ActivityLog> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        log.info("ActivityLogController initialized");

        timestampColumn.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getTimestamp()));
        timestampColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(DATE_FMT));
            }
        });

        actionColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getAction() != null ? cd.getValue().getAction().name() : ""));
        entityTypeColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getEntityType() != null ? cd.getValue().getEntityType().name() : ""));
        entityIdColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getEntityId() != null ? String.valueOf(cd.getValue().getEntityId()) : ""));
        descriptionColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getDescription() != null ? cd.getValue().getDescription() : ""));

        logTable.setItems(tableData);

        actionFilter.getItems().add(I18n.get("activity.allActions"));
        for (ActivityAction action : ActivityAction.values()) {
            actionFilter.getItems().add(action.name());
        }
        actionFilter.getSelectionModel().selectFirst();

        entityTypeFilter.getItems().add(I18n.get("activity.allEntityTypes"));
        for (EntityType type : EntityType.values()) {
            entityTypeFilter.getItems().add(type.name());
        }
        entityTypeFilter.getSelectionModel().selectFirst();

        loadLogs();
    }

    @FXML
    private void onApplyFilters() {
        loadLogs();
    }

    private void loadLogs() {
        List<ActivityLog> allLogs = storeManager.getRoot().getActivityLogs();

        String actionFilterValue = actionFilter.getValue();
        String entityTypeFilterValue = entityTypeFilter.getValue();

        var filtered = allLogs.stream()
                .filter(l -> actionFilterValue == null || I18n.get("activity.allActions").equals(actionFilterValue)
                        || (l.getAction() != null && l.getAction().name().equals(actionFilterValue)))
                .filter(l -> entityTypeFilterValue == null || I18n.get("activity.allEntityTypes").equals(entityTypeFilterValue)
                        || (l.getEntityType() != null && l.getEntityType().name().equals(entityTypeFilterValue)))
                .toList();

        tableData.setAll(filtered);
    }
}
