package org.fintrax.ui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
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
import org.fintrax.ui.control.TableColumnVisibilityMenu;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

final class RuleReviewDialog {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    record Services(RuleService ruleService, AccountService accountService, CategoryService categoryService,
                    LabelService labelService, SettingsService settingsService) {}

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

        TableView<TransactionRulePreview> table = new TableView<>();
        table.setId("ruleReviewTable");
        table.getStyleClass().add("dense");
        List<TableColumn<TransactionRulePreview, String>> columns = List.of(
                column("rules.review.date", row -> row.bookingDate() == null
                        ? "—" : row.bookingDate().format(DATE_FORMAT), 100),
                column("rules.review.payee", TransactionRulePreview::payee, 180),
                column("rules.review.amount", row -> row.amount() == null
                        ? "—" : String.format("%,.2f EUR", row.amount().doubleValue()), 110),
                column("rules.review.account", this::accountName, 140),
                column("rules.review.category", row -> change(categoryName(row.currentCategoryId()),
                        categoryName(row.proposedCategoryId())), 190),
                column("rules.review.labels", row -> change(labels(row.currentLabelIds()),
                        labels(row.proposedLabelIds())), 220),
                column("rules.review.note", row -> change(display(row.currentNote()),
                        display(row.proposedNote())), 220),
                column("rules.review.matchedRules", this::matchedRules, 200));
        table.getColumns().setAll(columns);

        MenuButton columnMenuButton = new MenuButton();
        columnMenuButton.setId("ruleReviewColumnMenuButton");
        columnMenuButton.setAccessibleText(I18n.get("button.columns"));
        columnMenuButton.getStyleClass().add("column-menu-button");
        TableColumn<TransactionRulePreview, String> menuColumn = new TableColumn<>();
        menuColumn.setMinWidth(34);
        menuColumn.setPrefWidth(34);
        menuColumn.setMaxWidth(34);
        menuColumn.setResizable(false);
        menuColumn.setSortable(false);
        menuColumn.setReorderable(false);
        menuColumn.setGraphic(columnMenuButton);
        table.getColumns().add(menuColumn);
        TableColumnVisibilityMenu.install(table, columnMenuButton, "rules.review.columns", settingsService,
                List.of(new TableColumnVisibilityMenu.Column("date", columns.get(0)),
                        new TableColumnVisibilityMenu.Column("payee", columns.get(1)),
                        new TableColumnVisibilityMenu.Column("amount", columns.get(2)),
                        new TableColumnVisibilityMenu.Column("account", columns.get(3)),
                        new TableColumnVisibilityMenu.Column("category", columns.get(4)),
                        new TableColumnVisibilityMenu.Column("labels", columns.get(5)),
                        new TableColumnVisibilityMenu.Column("note", columns.get(6)),
                        new TableColumnVisibilityMenu.Column("matchedRules", columns.get(7))));

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

        Runnable refreshPreview = () -> {
            RuleApplicationPreview preview = previewProvider.apply(onlyFillMissing.isSelected());
            currentPreview.set(preview);
            List<TransactionRulePreview> changedRows = preview.transactionsWithChanges();
            rows.setAll(changedRows);
            applyButton.setDisable(changedRows.isEmpty());
            summary.setText(I18n.get("rules.review.summary", changedRows.size(),
                    preview.matchingTransactionsWithoutChanges()));
            long multipleMatches = preview.matchingTransactions().stream()
                    .filter(row -> row.matchedRuleNames().size() > 1).count();
            multipleMatchWarning.setText(multipleMatches == 0 ? ""
                    : I18n.get(selectedRuleOnly
                    ? "rules.review.single.multipleMatches" : "rules.review.multipleMatches", multipleMatches));
            multipleMatchWarning.setVisible(multipleMatches > 0);
            multipleMatchWarning.setManaged(multipleMatches > 0);
            emptyResult.setVisible(changedRows.isEmpty());
            emptyResult.setManaged(changedRows.isEmpty());
            table.setVisible(!changedRows.isEmpty());
            table.setManaged(!changedRows.isEmpty());
        };
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
                ruleService.applyReviewedRules(currentPreview.get());
            }
        });
    }

    private <T> TableColumn<TransactionRulePreview, String> column(
            String titleKey, Function<TransactionRulePreview, String> value, double width) {
        TableColumn<TransactionRulePreview, String> column = new TableColumn<>(I18n.get(titleKey));
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
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

    private String change(String current, String proposed) {
        return current.equals(proposed) ? current : current + " → " + proposed;
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
