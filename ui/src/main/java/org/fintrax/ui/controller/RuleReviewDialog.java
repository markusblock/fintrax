package org.fintrax.ui.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.VBox;
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
import org.fintrax.ui.control.TableCellTooltips;
import org.fintrax.ui.control.TableColumnConfig;
import org.fintrax.ui.control.TableColumnVisibilityMenu;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

final class RuleReviewDialog {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

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
        dialog.setTitle(I18n.get("rules.review.title"));
        dialog.setHeaderText(I18n.get(selectedRuleOnly
                ? "rules.review.single.header" : "rules.review.header"));
        dialog.initOwner(owner);

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
        List<TableColumn<TransactionRulePreview, ?>> columns = List.of(
                column("rules.review.date", row -> row.bookingDate() == null
                        ? "—" : row.bookingDate().format(DATE_FORMAT), 100),
                column("rules.review.payee", TransactionRulePreview::payee, 180),
                column("rules.review.amount", row -> row.amount() == null
                        ? "—" : String.format("%,.2f EUR", row.amount().doubleValue()), 110),
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
        TableColumnVisibilityMenu.install(table, columnMenuButton, "rules.review.columns", settingsService,
                List.of(new TableColumnConfig("date", columns.get(0)),
                        new TableColumnConfig("payee", columns.get(1)),
                        new TableColumnConfig("amount", columns.get(2)),
                        new TableColumnConfig("account", columns.get(3)),
                        new TableColumnConfig("currentCategory", columns.get(4)),
                        new TableColumnConfig("categoryAfter", columns.get(5)),
                        new TableColumnConfig("currentLabel", columns.get(6)),
                        new TableColumnConfig("labelAfter", columns.get(7)),
                        new TableColumnConfig("matchingRuleCount", columns.get(8)),
                        new TableColumnConfig("matchedRules", columns.get(9))));
        Map<Long, BooleanProperty> selectionByTransactionId = new HashMap<>();
        TableColumn<TransactionRulePreview, Boolean> selectionColumn = new TableColumn<>();
        selectionColumn.setMinWidth(80);
        selectionColumn.setPrefWidth(80);
        selectionColumn.setMaxWidth(80);
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
        dialog.getDialogPane().setContent(content);

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

    private FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(literal);
        icon.setIconSize(size);
        return icon;
    }
}
