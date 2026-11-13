package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Category;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.RuleApplicationPreview;
import org.fintrax.service.RuleService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.TransactionRulePreview;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.WindowGeometry;
import org.fintrax.ui.control.SingleLineText;
import org.fintrax.ui.control.TableCellTooltips;
import org.fintrax.ui.control.TableColumnConfig;
import org.fintrax.ui.control.TableColumnVisibilityMenu;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

final class RuleReviewDialog {
    private static final double SELECTION_COLUMN_WIDTH = 36;
    private static final double MIN_REVIEW_WINDOW_WIDTH = 800;
    private static final double MIN_REVIEW_WINDOW_HEIGHT = 400;
    private static final String REVIEW_WINDOW_GEOMETRY_SETTING = "rules.review.window.geometry";
    private static final String REVIEW_COLUMNS_SETTING = "rules.review.columns";
    private static final String PAYEE_COLUMN_KEY = "payee";
    private static final String PURPOSE_COLUMN_KEY = "purpose";
    record Services(RuleService ruleService, AccountService accountService, CategoryService categoryService,
                    LabelService labelService, SettingsService settingsService) {}

    private record PreviewElements(Label summary, Label multipleMatchWarning, Label emptyResult,
                                   TableView<TransactionRulePreview> table) {}

    private final Window owner;
    private final RuleService ruleService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final SettingsService settingsService;
    private final Function<Boolean, RuleApplicationPreview> previewProvider;
    private final boolean selectedRuleOnly;

    RuleReviewDialog(Window owner, Services services,
                     Function<Boolean, RuleApplicationPreview> previewProvider, boolean selectedRuleOnly) {
        this.owner = owner;
        this.ruleService = services.ruleService();
        this.accountService = services.accountService();
        this.categoryService = services.categoryService();
        this.labelService = services.labelService();
        this.settingsService = services.settingsService();
        this.previewProvider = previewProvider;
        this.selectedRuleOnly = selectedRuleOnly;
    }

    void show() {
        Dialog<ButtonType> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.setResizable(true);
        dialog.setTitle(I18n.get("rules.review.title"));
        dialog.setHeaderText(I18n.get(selectedRuleOnly
                ? "rules.review.single.header" : "rules.review.header"));
        dialog.initOwner(owner);
        dialog.setOnShown(event -> {
            Stage stage = dialogStage(dialog);
            stage.setOnHiding(windowEvent ->
                    WindowGeometry.save(stage, settingsService, REVIEW_WINDOW_GEOMETRY_SETTING,
                            MIN_REVIEW_WINDOW_WIDTH, MIN_REVIEW_WINDOW_HEIGHT));
            // Dialog layout can override restored dimensions during its initial layout pass.
            Platform.runLater(() -> {
                if (stage.isShowing()) {
                    WindowGeometry.restore(stage, settingsService, REVIEW_WINDOW_GEOMETRY_SETTING,
                            MIN_REVIEW_WINDOW_WIDTH, MIN_REVIEW_WINDOW_HEIGHT);
                }
            });
        });

        CheckBox onlyFillMissing = new CheckBox(I18n.get("rules.review.onlyFillMissing"));
        onlyFillMissing.setId("onlyFillMissingRules");
        onlyFillMissing.setSelected(true);
        CheckBox selectAllCheckbox = new CheckBox();
        selectAllCheckbox.setId("ruleReviewSelectAllCheckbox");
        selectAllCheckbox.setAllowIndeterminate(true);
        Tooltip selectAllTooltip = new Tooltip(I18n.get("button.selectAll"));
        selectAllCheckbox.setTooltip(selectAllTooltip);
        selectAllCheckbox.setAccessibleText(selectAllTooltip.getText());

        TableView<TransactionRulePreview> table = new TableView<>();
        table.setId("ruleReviewTable");
        table.getStyleClass().add("dense");
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        TableColumn<TransactionRulePreview, String> amountColumn = column("rules.review.amount",
                row -> row.amount() == null ? "—" : String.format("%,.2f", row.amount().doubleValue()), 110);
        amountColumn.setCellFactory(column -> new TableCell<>() {
            {
                setAlignment(Pos.CENTER_RIGHT);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
        List<TableColumn<TransactionRulePreview, ?>> columns = List.of(
                dateColumn("col.bookingDate", TransactionRulePreview::bookingDate, dateFormatter),
                dateColumn("col.valueDate", TransactionRulePreview::valueDate, dateFormatter),
                column("rules.review.payee", TransactionRulePreview::payee, 180),
                column("col.purpose", row -> SingleLineText.normalize(row.purpose()), 180),
                amountColumn,
                column("rules.review.account", this::accountName, 140),
                column("rules.review.currentCategory", row -> categoryName(row.currentCategoryId()), 150),
                column("rules.review.categoryAfter", row -> categoryName(row.proposedCategoryId()), 150),
                column("rules.review.currentLabel", row -> display(labels(row.currentLabelIds())), 160),
                column("rules.review.labelAfter", row -> display(labels(row.proposedLabelIds())), 160),
                column("rules.review.matchingRuleCount", row -> row.matchedRuleNames().size(), 50),
                column("rules.review.matchedRules", this::matchedRules, 200));
        table.getColumns().setAll(columns);

        MenuButton columnMenuButton = new MenuButton();
        columnMenuButton.setId("ruleReviewColumnMenuButton");
        columnMenuButton.setAccessibleText(I18n.get("button.columns"));
        columnMenuButton.getStyleClass().add("column-menu-button");
        TableColumn<TransactionRulePreview, String> menuColumn = new TableColumn<>();
        menuColumn.setMinWidth(17);
        menuColumn.setPrefWidth(17);
        menuColumn.setMaxWidth(17);
        menuColumn.setResizable(false);
        menuColumn.setSortable(false);
        menuColumn.setReorderable(false);
        menuColumn.setGraphic(columnMenuButton);
        table.getColumns().add(menuColumn);
        migratePurposeColumnOrder();
        TableColumnVisibilityMenu.install(table, columnMenuButton, REVIEW_COLUMNS_SETTING, settingsService,
                List.of(new TableColumnConfig("date", columns.get(0)),
                        new TableColumnConfig("valueDate", columns.get(1)),
                        new TableColumnConfig(PAYEE_COLUMN_KEY, columns.get(2)),
                        new TableColumnConfig(PURPOSE_COLUMN_KEY, columns.get(3)),
                        new TableColumnConfig("amount", columns.get(4)),
                        new TableColumnConfig("account", columns.get(5)),
                        new TableColumnConfig("currentCategory", columns.get(6)),
                        new TableColumnConfig("categoryAfter", columns.get(7)),
                        new TableColumnConfig("currentLabel", columns.get(8)),
                        new TableColumnConfig("labelAfter", columns.get(9)),
                        new TableColumnConfig("matchingRuleCount", columns.get(10)),
                        new TableColumnConfig("matchedRules", columns.get(11))));
        Map<Long, BooleanProperty> selectionByTransactionId = new HashMap<>();
        table.setRowFactory(view -> createReviewRow(view, selectionByTransactionId));
        TableColumn<TransactionRulePreview, Boolean> selectionColumn = new TableColumn<>();
        selectionColumn.setMinWidth(SELECTION_COLUMN_WIDTH);
        selectionColumn.setPrefWidth(SELECTION_COLUMN_WIDTH);
        selectionColumn.setMaxWidth(SELECTION_COLUMN_WIDTH);
        selectionColumn.setResizable(false);
        selectionColumn.setSortable(false);
        selectionColumn.setReorderable(false);
        selectionColumn.setGraphic(selectAllCheckbox);
        selectionColumn.setCellValueFactory(cell -> selectionByTransactionId.get(
                cell.getValue().transactionId()));
        selectionColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectionColumn));
        table.getColumns().add(0, selectionColumn);
        TableCellTooltips.install(table);

        Label summary = new Label();
        summary.setId("ruleReviewSummary");
        summary.getStyleClass().add("secondary-text");
        Label multipleMatchWarning = new Label();
        multipleMatchWarning.setId("ruleReviewMultipleMatchWarning");
        multipleMatchWarning.getStyleClass().add("warning-copy");
        Label emptyResult = new Label(I18n.get("rules.review.empty"));
        emptyResult.setId("ruleReviewEmptyResult");
        emptyResult.getStyleClass().add("empty-state");
        emptyResult.setVisible(false);
        emptyResult.setManaged(false);

        ButtonType applyButtonType = new ButtonType(I18n.get("button.applyChanges"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType(I18n.get("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(applyButtonType, cancelButtonType);
        Button applyButton = (Button) dialog.getDialogPane().lookupButton(applyButtonType);
        applyButton.setId("applyReviewedRulesButton");
        applyButton.setGraphic(icon("fth-check", 14));
        applyButton.getStyleClass().add("accent-button");
        ((Button) dialog.getDialogPane().lookupButton(cancelButtonType)).setId("cancelRuleReviewButton");

        ObservableList<TransactionRulePreview> rows = FXCollections.observableArrayList();
        table.setItems(rows);
        AtomicReference<RuleApplicationPreview> currentPreview = new AtomicReference<>();
        PreviewElements previewElements = new PreviewElements(summary, multipleMatchWarning, emptyResult, table);
        Runnable updateSelectionControls = () -> updateSelectionControls(rows, selectionByTransactionId,
                applyButton, selectAllCheckbox, selectAllTooltip);
        selectAllCheckbox.setOnAction(event -> toggleAllRows(rows, selectionByTransactionId));
        Runnable refreshPreview = () -> updatePreview(onlyFillMissing.isSelected(), currentPreview, rows,
                selectionByTransactionId, updateSelectionControls, previewElements);
        onlyFillMissing.selectedProperty().addListener((observable, oldValue, newValue) -> refreshPreview.run());
        refreshPreview.run();

        VBox content = new VBox(10, onlyFillMissing, summary, multipleMatchWarning, emptyResult, table);
        content.getStyleClass().add("dialog-content");
        content.setMinWidth(1100);
        content.setPrefHeight(420);
        ScrollPane contentScrollPane = new ScrollPane(content);
        contentScrollPane.setFitToWidth(true);
        contentScrollPane.setMinHeight(0);
        contentScrollPane.setMinViewportHeight(0);
        contentScrollPane.setPrefViewportHeight(420);
        dialog.getDialogPane().setContent(contentScrollPane);

        dialog.setResultConverter(button -> button == applyButtonType ? applyButtonType : null);

        dialog.showAndWait().ifPresent(result -> {
            if (result == applyButtonType) {
                applySelectedChanges(currentPreview.get(), selectionByTransactionId);
            }
        });
    }

    private void updateSelectionControls(ObservableList<TransactionRulePreview> rows,
                                         Map<Long, BooleanProperty> selectionByTransactionId,
                                         Button applyButton, CheckBox selectAllCheckbox,
                                         Tooltip selectAllTooltip) {
        boolean hasSelectedRows = rows.stream()
                .anyMatch(row -> selectionByTransactionId.get(row.transactionId()).get());
        boolean allRowsSelected = !rows.isEmpty() && rows.stream()
                .allMatch(row -> selectionByTransactionId.get(row.transactionId()).get());
        applyButton.setDisable(!hasSelectedRows);
        selectAllCheckbox.setDisable(rows.isEmpty());
        selectAllCheckbox.setSelected(allRowsSelected);
        selectAllCheckbox.setIndeterminate(hasSelectedRows && !allRowsSelected);
        String actionText = I18n.get(allRowsSelected ? "button.deselectAll" : "button.selectAll");
        selectAllTooltip.setText(actionText);
        selectAllCheckbox.setAccessibleText(actionText);
    }

    private void toggleAllRows(ObservableList<TransactionRulePreview> rows,
                               Map<Long, BooleanProperty> selectionByTransactionId) {
        boolean selectRows = rows.stream()
                .anyMatch(row -> !selectionByTransactionId.get(row.transactionId()).get());
        rows.forEach(row -> selectionByTransactionId.get(row.transactionId()).set(selectRows));
    }

    private TableRow<TransactionRulePreview> createReviewRow(
            TableView<TransactionRulePreview> table,
            Map<Long, BooleanProperty> selectionByTransactionId) {
        TableRow<TransactionRulePreview> row = new TableRow<>();
        row.setContextMenu(new ContextMenu(
                selectionMenuItem("rules.review.selection.toggleSelection", table, row, selectionByTransactionId),
                selectionMenuItem("rules.review.selection.select", table, row, selectionByTransactionId, true),
                selectionMenuItem("rules.review.selection.deselect", table, row, selectionByTransactionId, false)));
        row.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if (row.isEmpty()) {
                event.consume();
            } else if (!row.isSelected()) {
                table.getSelectionModel().clearAndSelect(row.getIndex());
            }
        });
        return row;
    }

    private MenuItem selectionMenuItem(String titleKey, TableView<TransactionRulePreview> table,
                                       TableRow<TransactionRulePreview> contextRow,
                                       Map<Long, BooleanProperty> selectionByTransactionId) {
        MenuItem item = new MenuItem(I18n.get(titleKey));
        item.setOnAction(event -> contextMenuTargets(table, contextRow).forEach(row -> {
            BooleanProperty selected = selectionByTransactionId.get(row.transactionId());
            selected.set(!selected.get());
        }));
        return item;
    }

    private MenuItem selectionMenuItem(String titleKey, TableView<TransactionRulePreview> table,
                                       TableRow<TransactionRulePreview> contextRow,
                                       Map<Long, BooleanProperty> selectionByTransactionId,
                                       boolean selected) {
        MenuItem item = new MenuItem(I18n.get(titleKey));
        item.setOnAction(event -> contextMenuTargets(table, contextRow).forEach(row ->
                selectionByTransactionId.get(row.transactionId()).set(selected)));
        return item;
    }

    private List<TransactionRulePreview> contextMenuTargets(TableView<TransactionRulePreview> table,
                                                              TableRow<TransactionRulePreview> contextRow) {
        return contextRow.isSelected()
                ? List.copyOf(table.getSelectionModel().getSelectedItems())
                : List.of(contextRow.getItem());
    }

    private void updatePreview(boolean onlyFillMissing,
                               AtomicReference<RuleApplicationPreview> currentPreview,
                               ObservableList<TransactionRulePreview> rows,
                               Map<Long, BooleanProperty> selectionByTransactionId,
                               Runnable updateSelectionControls, PreviewElements elements) {
        RuleApplicationPreview preview = previewProvider.apply(onlyFillMissing);
        currentPreview.set(preview);
        List<TransactionRulePreview> changedRows = preview.transactionsWithChanges();
        initializeRowSelections(changedRows, selectionByTransactionId, updateSelectionControls);
        rows.setAll(changedRows);
        // Sort preferences are restored before the dialog's preview rows are loaded.
        elements.table().sort();
        updateSelectionControls.run();
        updatePreviewSummary(preview, changedRows, elements);
        updatePreviewVisibility(changedRows.isEmpty(), elements);
    }

    private void initializeRowSelections(List<TransactionRulePreview> rows,
                                         Map<Long, BooleanProperty> selectionByTransactionId,
                                         Runnable updateSelectionControls) {
        rows.forEach(row -> selectionByTransactionId.computeIfAbsent(row.transactionId(), id -> {
            BooleanProperty selected = new SimpleBooleanProperty(true);
            selected.addListener((observable, wasSelected, isSelected) -> updateSelectionControls.run());
            return selected;
        }));
    }

    private void updatePreviewSummary(RuleApplicationPreview preview, List<TransactionRulePreview> changedRows,
                                      PreviewElements elements) {
        elements.summary().setText(I18n.get("rules.review.summary", changedRows.size(),
                preview.matchingTransactionsWithoutChanges()));
        long multipleMatches = preview.matchingTransactions().stream()
                .filter(row -> row.matchedRuleNames().size() > 1).count();
        elements.multipleMatchWarning().setText(multipleMatches == 0 ? ""
                : I18n.get(selectedRuleOnly
                ? "rules.review.single.multipleMatches" : "rules.review.multipleMatches", multipleMatches));
        elements.multipleMatchWarning().setVisible(multipleMatches > 0);
        elements.multipleMatchWarning().setManaged(multipleMatches > 0);
    }

    private void updatePreviewVisibility(boolean empty, PreviewElements elements) {
        elements.emptyResult().setVisible(empty);
        elements.emptyResult().setManaged(empty);
        elements.table().setVisible(!empty);
        elements.table().setManaged(!empty);
    }

    private void applySelectedChanges(RuleApplicationPreview preview,
                                      Map<Long, BooleanProperty> selectionByTransactionId) {
        List<TransactionRulePreview> selectedChanges = preview.transactionsWithChanges().stream()
                .filter(row -> selectionByTransactionId.get(row.transactionId()).get())
                .toList();
        ruleService.applyReviewedRules(new RuleApplicationPreview(selectedChanges));
    }

    private <T> TableColumn<TransactionRulePreview, T> column(
            String titleKey, Function<TransactionRulePreview, T> value, double width) {
        TableColumn<TransactionRulePreview, T> column = new TableColumn<>(I18n.get(titleKey));
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleObjectProperty<>(value.apply(cell.getValue())));
        return column;
    }

    private TableColumn<TransactionRulePreview, String> dateColumn(
            String titleKey, Function<TransactionRulePreview, LocalDate> dateFor,
            DateTimeFormatter dateFormatter) {
        TableColumn<TransactionRulePreview, String> column = column(
                titleKey, row -> formatDate(dateFor.apply(row), dateFormatter), 105);
        column.setComparator((first, second) -> compareDates(first, second, dateFormatter));
        return column;
    }

    private int compareDates(String first, String second, DateTimeFormatter dateFormatter) {
        if ("—".equals(first)) return "—".equals(second) ? 0 : -1;
        if ("—".equals(second)) return 1;
        return LocalDate.parse(first, dateFormatter).compareTo(LocalDate.parse(second, dateFormatter));
    }

    private String formatDate(LocalDate date, DateTimeFormatter dateFormatter) {
        return date == null ? "—" : date.format(dateFormatter);
    }

    private String accountName(TransactionRulePreview row) {
        if (row.accountId() == null) return "—";
        return accountService.getAccount(row.accountId())
                .map(account -> {
                    String bankName = account.getBankName() == null ? "" : account.getBankName();
                    String iban = maskedIban(account.getIban());
                    String details = iban.isBlank() ? bankName : bankName + " (" + iban + ")";
                    return details.isBlank() ? String.valueOf(row.accountId()) : details;
                })
                .orElseGet(() -> String.valueOf(row.accountId()));
    }

    private String maskedIban(String iban) {
        if (iban == null || iban.length() < 8) return iban == null ? "" : iban;
        return iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) return "—";
        return categoryService.getCategory(categoryId).map(Category::getName).orElse("—");
    }

    private String labels(Set<Long> labelIds) {
        return labelIds.stream().sorted().map(id -> labelService.getLabel(id)
                        .map(org.fintrax.model.Label::getName).orElse(""))
                .filter(name -> !name.isBlank()).collect(Collectors.joining(", "));
    }

    private String matchedRules(TransactionRulePreview row) {
        String names = String.join(", ", row.matchedRuleNames());
        return row.matchedRuleNames().size() > 1
                ? I18n.get("rules.review.multipleRuleNames", row.matchedRuleNames().size(), names)
                : names;
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private void migratePurposeColumnOrder() {
        String orderSettingKey = REVIEW_COLUMNS_SETTING + ".order";
        String savedOrder = settingsService.getSettingValue(orderSettingKey);
        if (savedOrder == null || savedOrder.isBlank()) return;

        List<String> orderedColumns = new ArrayList<>(List.of(savedOrder.split(",")));
        if (orderedColumns.contains(PURPOSE_COLUMN_KEY)) return;

        int payeeIndex = orderedColumns.indexOf(PAYEE_COLUMN_KEY);
        int purposeIndex = payeeIndex < 0 ? orderedColumns.size() : payeeIndex + 1;
        orderedColumns.add(purposeIndex, PURPOSE_COLUMN_KEY);
        settingsService.saveSettingValue(orderSettingKey, String.join(",", orderedColumns));
    }

    private Stage dialogStage(Dialog<?> dialog) {
        return (Stage) dialog.getDialogPane().getScene().getWindow();
    }

    private FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(literal);
        icon.setIconSize(size);
        return icon;
    }
}
