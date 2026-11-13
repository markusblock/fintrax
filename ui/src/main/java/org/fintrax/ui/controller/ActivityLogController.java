package org.fintrax.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.SettingsService;
import org.fintrax.store.StoreManager;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.control.TableCellTooltips;
import org.fintrax.ui.control.TableColumnConfig;
import org.fintrax.ui.control.TableColumnVisibilityMenu;
import org.fintrax.ui.control.TableViewResizePolicy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
@Scope("prototype")
public class ActivityLogController {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String COLUMN_CONFIG_KEY = "activity.log.columns";
    private static final String INVALID_DATE_STYLE_CLASS = "invalid-date";
    private static final String DIAGNOSTIC_DETAILS_MARKER = "\n[FINTRAX_SYNC_DIAGNOSTICS]\n";
    private static final List<String> LEGACY_STACK_TRACE_HEADERS = List.of(
            "\nSanitized stack trace:", "\nBereinigter Stacktrace:");

    @FXML
    private BorderPane rootPane;
    @FXML
    private TableView<ActivityLog> logTable;
    @FXML
    private Button detailsButton;
    @FXML
    private MenuButton columnMenuButton;
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
    @FXML
    private ComboBox<TransactionDateFilters.Preset> datePresetFilter;
    @FXML
    private DatePicker fromDateFilter;
    @FXML
    private DatePicker toDateFilter;

    private final StoreManager storeManager;
    private final SettingsService settingsService;
    private final ObservableList<ActivityLog> tableData = FXCollections.observableArrayList();
    private boolean updatingDateFilters;

    public ActivityLogController(StoreManager storeManager, SettingsService settingsService) {
        this.storeManager = storeManager;
        this.settingsService = settingsService;
    }

    @FXML
    public void initialize() {
        log.info("ActivityLogController initialized");
        setupDateFilters();
        setupTable();
        setupActionAndEntityFilters();
        loadLogs();
        setupColumnMenu();
        restoreOrInitializeSortOrder();
    }

    private void setupTable() {
        TableViewResizePolicy.fitColumnsAndAllowHorizontalScrolling(logTable);
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
                tableDescription(cd.getValue().getDescription())));
        TableCellTooltips.install(logTable);
        logTable.setRowFactory(table -> {
            TableRow<ActivityLog> row = new TableRow<>();
            row.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    logTable.getSelectionModel().select(row.getIndex());
                    showDetails(row.getItem());
                }
            });
            return row;
        });

        logTable.setItems(tableData);
        detailsButton.disableProperty().bind(logTable.getSelectionModel().selectedItemProperty().isNull());
    }

    private void setupActionAndEntityFilters() {
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
        actionFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadLogs());
        entityTypeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadLogs());
    }

    private void setupDateFilters() {
        Locale locale = I18n.getCurrentLocale();
        DateTimeFormatter dateFormatter = TransactionDateFilters.formatter(locale);
        datePresetFilter.getItems().setAll(TransactionDateFilters.Preset.values());
        datePresetFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(TransactionDateFilters.Preset preset) {
                return preset == null ? "" : I18n.get("filter.datePreset." + preset.name());
            }

            @Override
            public TransactionDateFilters.Preset fromString(String text) {
                return Arrays.stream(TransactionDateFilters.Preset.values())
                        .filter(preset -> toString(preset).equals(text))
                        .findFirst()
                        .orElse(null);
            }
        });

        StringConverter<LocalDate> dateConverter = new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : dateFormatter.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                return TransactionDateFilters.parseDate(text, locale);
            }
        };
        fromDateFilter.setConverter(dateConverter);
        toDateFilter.setConverter(dateConverter);
        datePresetFilter.setValue(TransactionDateFilters.Preset.ALL_TIME);

        datePresetFilter.valueProperty().addListener((observable, oldPreset, preset) -> {
            if (updatingDateFilters || preset == null) {
                return;
            }
            if (preset != TransactionDateFilters.Preset.CUSTOM) {
                TransactionDateFilters.Range range = TransactionDateFilters.rangeFor(
                        preset, LocalDate.now(ZoneId.systemDefault()));
                updatingDateFilters = true;
                fromDateFilter.setValue(range.fromDate());
                toDateFilter.setValue(range.toDate());
                updatingDateFilters = false;
                setDateFieldInvalid(fromDateFilter, false);
                setDateFieldInvalid(toDateFilter, false);
            }
            loadLogs();
        });
        fromDateFilter.valueProperty().addListener((observable, oldDate, newDate) -> onDateFilterChanged());
        toDateFilter.valueProperty().addListener((observable, oldDate, newDate) -> onDateFilterChanged());
        fromDateFilter.getEditor().textProperty().addListener(
                (observable, oldText, newText) -> onDateTextChanged());
        toDateFilter.getEditor().textProperty().addListener(
                (observable, oldText, newText) -> onDateTextChanged());
    }

    private void onDateFilterChanged() {
        if (updatingDateFilters) {
            return;
        }
        setCustomDatePreset();
        loadLogs();
    }

    private void restoreOrInitializeSortOrder() {
        // Apply the first-use default after populating rows so the table retains its sort order.
        String savedSortOrder = settingsService.getSettingValue(COLUMN_CONFIG_KEY + ".sort");
        if (savedSortOrder == null || savedSortOrder.isBlank()) {
            setDefaultSortOrder();
        }
    }

    private void onDateTextChanged() {
        if (updatingDateFilters) {
            return;
        }
        setDateFieldInvalid(fromDateFilter, false);
        setDateFieldInvalid(toDateFilter, false);
        setCustomDatePreset();
        loadLogs();
    }

    private void setCustomDatePreset() {
        if (datePresetFilter.getValue() == TransactionDateFilters.Preset.CUSTOM) {
            return;
        }
        updatingDateFilters = true;
        datePresetFilter.setValue(TransactionDateFilters.Preset.CUSTOM);
        updatingDateFilters = false;
    }

    private void setupColumnMenu() {
        TableColumnVisibilityMenu.install(logTable, columnMenuButton, COLUMN_CONFIG_KEY, settingsService,
                List.of(new TableColumnConfig("timestamp", timestampColumn),
                        new TableColumnConfig("action", actionColumn),
                        new TableColumnConfig("entityType", entityTypeColumn),
                        new TableColumnConfig("entityId", entityIdColumn),
                        new TableColumnConfig("description", descriptionColumn)), this::setDefaultSortOrder);
    }

    private void setDefaultSortOrder() {
        timestampColumn.setSortType(TableColumn.SortType.DESCENDING);
        logTable.getSortOrder().setAll(timestampColumn);
    }

    @FXML
    private void onViewDetails() {
        ActivityLog selected = logTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        showDetails(selected);
    }

    private void showDetails(ActivityLog selected) {
        Dialog<Void> details = new Dialog<>();
        details.setTitle(I18n.get("dialog.activityDetails.title"));
        details.setHeaderText(I18n.get("dialog.activityDetails.header"));
        TextArea description = new TextArea(detailsDescription(selected.getDescription()));
        description.setEditable(false);
        description.setWrapText(true);
        description.setPrefRowCount(16);
        description.setPrefColumnCount(72);

        javafx.scene.control.Label copyStatus = new javafx.scene.control.Label();
        Button copyButton = new Button(I18n.get("button.copy"));
        copyButton.getStyleClass().add("button-outlined");
        copyButton.setOnAction(event -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(description.getText());
            boolean copied;
            try {
                copied = Clipboard.getSystemClipboard().setContent(clipboardContent);
            } catch (RuntimeException ignored) {
                copied = false;
            }
            copyStatus.setText(I18n.get(copied
                    ? "message.clipboard.copied" : "message.clipboard.copyFailed"));
        });
        HBox actions = new HBox(10, copyButton, copyStatus);
        VBox content = new VBox(10, description, actions);
        VBox.setVgrow(description, Priority.ALWAYS);
        details.getDialogPane().setContent(content);
        details.getDialogPane().getButtonTypes().setAll(
                new ButtonType(I18n.get("button.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
        details.getDialogPane().setPrefSize(700, 460);
        details.initOwner(rootPane.getScene().getWindow());
        FintraxUI.configureDialog(details);
        details.showAndWait();
    }

    private String tableDescription(String description) {
        if (description == null) {
            return "";
        }
        int diagnosticsStart = diagnosticDetailsStart(description);
        return diagnosticsStart < 0 ? description : description.substring(0, diagnosticsStart).stripTrailing();
    }

    private String detailsDescription(String description) {
        if (description == null) {
            return "";
        }
        int diagnosticsStart = description.indexOf(DIAGNOSTIC_DETAILS_MARKER);
        if (diagnosticsStart < 0) {
            return description;
        }
        String summary = description.substring(0, diagnosticsStart).stripTrailing();
        String diagnostics = description.substring(diagnosticsStart + DIAGNOSTIC_DETAILS_MARKER.length()).stripLeading();
        return summary + "\n\n" + I18n.get("activity.sync.diagnosticsHeading") + "\n" + diagnostics;
    }

    private int diagnosticDetailsStart(String description) {
        int diagnosticsStart = description.indexOf(DIAGNOSTIC_DETAILS_MARKER);
        if (diagnosticsStart >= 0) {
            return diagnosticsStart;
        }
        return LEGACY_STACK_TRACE_HEADERS.stream()
                .mapToInt(description::indexOf)
                .filter(index -> index >= 0)
                .min()
                .orElse(-1);
    }

    private void loadLogs() {
        List<ActivityLog> allLogs = storeManager.getRoot().getActivityLogs();
        Optional<DateRange> dateRange = getDateRange();
        if (dateRange.isEmpty()) {
            return;
        }

        String actionFilterValue = actionFilter.getValue();
        String entityTypeFilterValue = entityTypeFilter.getValue();

        var filtered = allLogs.stream()
                .filter(l -> actionFilterValue == null || I18n.get("activity.allActions").equals(actionFilterValue)
                        || (l.getAction() != null && l.getAction().name().equals(actionFilterValue)))
                .filter(l -> entityTypeFilterValue == null || I18n.get("activity.allEntityTypes").equals(entityTypeFilterValue)
                        || (l.getEntityType() != null && l.getEntityType().name().equals(entityTypeFilterValue)))
                .filter(log -> isWithinDateRange(log, dateRange.get()))
                .toList();

        tableData.setAll(filtered);
    }

    private Optional<DateRange> getDateRange() {
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = TransactionDateFilters.parseDate(fromDateFilter.getEditor().getText(), I18n.getCurrentLocale());
        } catch (DateTimeParseException e) {
            setDateFieldInvalid(fromDateFilter, true);
            return Optional.empty();
        }
        try {
            toDate = TransactionDateFilters.parseDate(toDateFilter.getEditor().getText(), I18n.getCurrentLocale());
        } catch (DateTimeParseException e) {
            setDateFieldInvalid(toDateFilter, true);
            return Optional.empty();
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            setDateFieldInvalid(fromDateFilter, true);
            setDateFieldInvalid(toDateFilter, true);
            return Optional.empty();
        }
        setDateFieldInvalid(fromDateFilter, false);
        setDateFieldInvalid(toDateFilter, false);
        return Optional.of(new DateRange(fromDate, toDate));
    }

    private boolean isWithinDateRange(ActivityLog log, DateRange range) {
        if (range.fromDate() == null && range.toDate() == null) {
            return true;
        }
        if (log.getTimestamp() == null) {
            return false;
        }
        LocalDate date = log.getTimestamp().toLocalDate();
        return (range.fromDate() == null || !date.isBefore(range.fromDate()))
                && (range.toDate() == null || !date.isAfter(range.toDate()));
    }

    private void setDateFieldInvalid(DatePicker datePicker, boolean invalid) {
        if (invalid) {
            if (!datePicker.getEditor().getStyleClass().contains(INVALID_DATE_STYLE_CLASS)) {
                datePicker.getEditor().getStyleClass().add(INVALID_DATE_STYLE_CLASS);
            }
        } else {
            datePicker.getEditor().getStyleClass().remove(INVALID_DATE_STYLE_CLASS);
        }
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {
    }
}
