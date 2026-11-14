package org.fintrax.ui;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.RuleService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.TransactionService;
import org.fintrax.service.TransactionRulePreview;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class RulesControllerTest extends AbstractUITest {
    private Transaction transaction;
    private Category currentCategory;
    private Category proposedCategory;
    private Long proposedLabelId;
    private String proposedLabelName;
    private String ruleName;
    private String disabledRuleName;
    private String mergeSourceOneName;
    private String mergeSourceTwoName;
    private String mergeAmountSourceName;
    private String transactionPurpose;

    @Override
    public void start(Stage stage) throws Exception {
        AccountService accountService = applicationContext.getBean(AccountService.class);
        CategoryService categoryService = applicationContext.getBean(CategoryService.class);
        LabelService labelService = applicationContext.getBean(LabelService.class);
        RuleService ruleService = applicationContext.getBean(RuleService.class);
        TransactionService transactionService = applicationContext.getBean(TransactionService.class);

        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Review Bank", "User", AccountType.GIRO, null);
        String suffix = Long.toString(System.nanoTime());
        transactionPurpose = "Review purpose " + suffix;
        currentCategory = categoryService.createCategory("Review Current " + suffix, null, null);
        proposedCategory = categoryService.createCategory("Review Proposed " + suffix, null, null);
        var proposedLabel = labelService.createLabel("Review label " + suffix, null, null);
        proposedLabelId = proposedLabel.getId();
        proposedLabelName = proposedLabel.getName();
        ruleName = "Review rule " + suffix;
        ruleService.createRule(ruleName, List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME).operator(Operator.CONTAINS).value("REVIEW-" + suffix).build()),
                List.of(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                                .categoryId(proposedCategory.getId()).build(),
                        RuleAction.builder().actionType(ActionType.ADD_LABEL)
                                .labelId(proposedLabelId).build()));
        disabledRuleName = "Disabled review rule " + suffix;
        Rule disabledRule = ruleService.createRule(disabledRuleName, List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME).operator(Operator.CONTAINS).value("REVIEW-" + suffix).build(),
                        RuleCondition.builder().matchField(MatchField.PAYEE_NAME)
                                .operator(Operator.CONTAINS).value("purchase").build()),
                List.of(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(proposedCategory.getId()).build()));
        ruleService.toggleRule(disabledRule.getId());
        mergeSourceOneName = "Merge source one " + suffix;
        mergeSourceTwoName = "Merge source two " + suffix;
        ruleService.createRule(mergeSourceOneName, List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME).operator(Operator.CONTAINS).value("MERGE-ONE").build()),
                List.of(RuleAction.builder().actionType(ActionType.ADD_LABEL).labelId(proposedLabelId).build()));
        ruleService.createRule(mergeSourceTwoName, List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME).operator(Operator.CONTAINS).value("MERGE-TWO").build()),
                List.of(RuleAction.builder().actionType(ActionType.ADD_LABEL).labelId(proposedLabelId).build()));
        mergeAmountSourceName = "Merge amount source " + suffix;
        ruleService.createRule(mergeAmountSourceName, List.of(RuleCondition.builder()
                        .matchField(MatchField.AMOUNT).operator(Operator.AMOUNT_RANGE)
                        .amountMin(new BigDecimal("1234.56")).amountMax(new BigDecimal("1234.57")).build()),
                List.of(RuleAction.builder().actionType(ActionType.ADD_LABEL).labelId(proposedLabelId).build()));
        transaction = transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("REVIEW-" + suffix + " purchase").amount(BigDecimal.TEN)
                .bookingDate(LocalDate.of(2025, 2, 1)).valueDate(LocalDate.of(2025, 2, 4))
                .purpose(transactionPurpose).categoryId(currentCategory.getId()).note("Current note").build());
        Parent root = applicationContext.getBean(ViewLoader.class).load("rules");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void applyingRulesShowsReviewAndCancelLeavesTransactionsUnchanged() {
        openReviewDialog();
        DialogPane reviewDialogPane = lookup(".dialog-pane").query();
        assertTrue(((Stage) reviewDialogPane.getScene().getWindow()).isResizable());

        CheckBox onlyFillMissing = lookup("#onlyFillMissingRules").query();
        assertTrue(onlyFillMissing.isSelected());
        TableView<TransactionRulePreview> reviewTable = lookup("#ruleReviewTable").query();
        MenuButton reviewColumnMenu = lookup("#ruleReviewColumnMenuButton").query();
        assertSame(reviewColumnMenu, reviewTable.getColumns().getLast().getGraphic());
        assertEquals(14, reviewColumnMenu.getItems().size());
        assertEquals(I18n.get("button.columns.reset"), reviewColumnMenu.getItems().getLast().getText());
        assertTrue(reviewColumnMenu.getItems().stream()
                .noneMatch(item -> "Note (current -> proposed)".equals(item.getText())));
        TransactionRulePreview initialRow = rowFor(reviewTable, transaction.getId());
        assertEquals(currentCategory.getId(), initialRow.proposedCategoryId());
        assertTrue(initialRow.proposedLabelIds().contains(proposedLabelId));
        TableColumn<TransactionRulePreview, ?> bookingDateColumn = reviewTable.getColumns().stream()
                .filter(column -> I18n.get("col.bookingDate").equals(column.getText()))
                .findFirst().orElseThrow();
        TableColumn<TransactionRulePreview, ?> valueDateColumn = reviewTable.getColumns().stream()
                .filter(column -> I18n.get("col.valueDate").equals(column.getText()))
                .findFirst().orElseThrow();
        assertEquals(formatDate(transaction.getBookingDate()), bookingDateColumn.getCellData(initialRow));
        assertEquals(formatDate(transaction.getValueDate()), valueDateColumn.getCellData(initialRow));
        TableColumn<TransactionRulePreview, ?> amountColumn = reviewTable.getColumns().stream()
                .filter(column -> I18n.get("rules.review.amount").equals(column.getText()))
                .findFirst().orElseThrow();
        assertEquals(String.format("%,.2f", transaction.getAmount().doubleValue()),
                amountColumn.getCellData(initialRow));
        TableCell<?, ?> amountCell = reviewTable.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == amountColumn
                        && cell.getTableRow() != null
                        && cell.getTableRow().getItem() instanceof TransactionRulePreview preview
                        && preview.transactionId().equals(transaction.getId()))
                .findFirst().orElseThrow();
        assertEquals(Pos.CENTER_RIGHT, amountCell.getAlignment());
        assertEquals(transactionPurpose, reviewTable.getColumns().stream()
                .filter(column -> I18n.get("col.purpose").equals(column.getText()))
                .findFirst().orElseThrow().getCellData(initialRow));
        assertEquals(currentCategory.getName(), reviewTable.getColumns().stream()
                .filter(column -> column.getText().equals(I18n.get("rules.review.currentCategory")))
                .findFirst().orElseThrow().getCellObservableValue(initialRow).getValue());
        assertEquals(currentCategory.getName(), reviewTable.getColumns().stream()
                .filter(column -> column.getText().equals(I18n.get("rules.review.categoryAfter")))
                .findFirst().orElseThrow().getCellObservableValue(initialRow).getValue());
        assertEquals("—", reviewTable.getColumns().stream()
                .filter(column -> column.getText().equals(I18n.get("rules.review.currentLabel")))
                .findFirst().orElseThrow().getCellObservableValue(initialRow).getValue());
        assertEquals(proposedLabelName, reviewTable.getColumns().stream()
                .filter(column -> column.getText().equals(I18n.get("rules.review.labelAfter")))
                .findFirst().orElseThrow().getCellObservableValue(initialRow).getValue());
        assertEquals(1, reviewTable.getColumns().stream()
                .filter(column -> column.getText().equals(I18n.get("rules.review.matchingRuleCount")))
                .findFirst().orElseThrow().getCellObservableValue(initialRow).getValue());

        var originalReviewColumnOrder = List.copyOf(reviewTable.getColumns());
        CheckMenuItem currentCategoryItem = reviewColumnMenu.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> I18n.get("rules.review.currentCategory").equals(item.getText()))
                .findFirst().orElseThrow();
        try {
            interact(() -> currentCategoryItem.setSelected(false));
            assertFalse(reviewTable.getColumns().stream()
                    .filter(column -> I18n.get("rules.review.currentCategory").equals(column.getText()))
                    .findFirst().orElseThrow().isVisible());
            assertTrue(applicationContext.getBean(SettingsService.class)
                    .getSettingValue("rules.review.columns").contains("currentCategory=false"));
            interact(() -> {
                var dateColumn = reviewTable.getColumns().remove(1);
                reviewTable.getColumns().add(2, dateColumn);
            });
            assertEquals("valueDate,date,payee,purpose,amount,account,currentCategory,categoryAfter,currentLabel,labelAfter,matchingRuleCount,matchedRules",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("rules.review.columns.order"));
        } finally {
            interact(() -> {
                currentCategoryItem.setSelected(true);
                reviewTable.getColumns().setAll(originalReviewColumnOrder);
            });
        }

        interact(() -> onlyFillMissing.setSelected(false));
        assertFalse(onlyFillMissing.isSelected());
        TransactionRulePreview updatedPreview = rowFor(reviewTable, transaction.getId());
        assertEquals(proposedCategory.getId(), updatedPreview.proposedCategoryId());
        assertEquals(proposedCategory.getName(), reviewTable.getColumns().stream()
                .filter(column -> column.getText().equals(I18n.get("rules.review.categoryAfter")))
                .findFirst().orElseThrow().getCellObservableValue(updatedPreview).getValue());

        Button cancelButton = lookup("#cancelRuleReviewButton").query();
        interact(cancelButton::fire);

        Transaction unchanged = applicationContext.getBean(TransactionService.class)
                .getTransaction(transaction.getId()).orElseThrow();
        assertNotEquals(proposedCategory.getId(), unchanged.getCategoryId());
        assertEquals("Current note", unchanged.getNote());
    }

    @Test
    void resizingReviewKeepsApplyAndCancelButtonsVisible() {
        openReviewDialog();
        DialogPane dialogPane = lookup(".dialog-pane").query();
        assertInstanceOf(ScrollPane.class, dialogPane.getContent());
        Stage dialogStage = (Stage) dialogPane.getScene().getWindow();
        double initialHeight = dialogStage.getHeight();
        Button applyButton = lookup("#applyReviewedRulesButton").query();
        Button cancelButton = lookup("#cancelRuleReviewButton").query();

        interact(() -> dialogStage.setHeight(420));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(dialogStage.getHeight() < initialHeight);
        Bounds dialogBounds = dialogPane.localToScene(dialogPane.getBoundsInLocal());
        for (Button actionButton : List.of(applyButton, cancelButton)) {
            Bounds actionBounds = actionButton.localToScene(actionButton.getBoundsInLocal());
            assertTrue(actionButton.isVisible());
            assertTrue(actionBounds.getMinY() >= dialogBounds.getMinY());
            assertTrue(actionBounds.getMaxY() <= dialogBounds.getMaxY());
        }
        interact(cancelButton::fire);
    }

    @Test
    void confirmingReviewAppliesProposedChanges() {
        openReviewDialog();
        CheckBox onlyFillMissing = lookup("#onlyFillMissingRules").query();
        interact(() -> onlyFillMissing.setSelected(false));

        Button applyButton = lookup("#applyReviewedRulesButton").query();
        interact(applyButton::fire);

        Transaction updated = applicationContext.getBean(TransactionService.class)
                .getTransaction(transaction.getId()).orElseThrow();
        assertEquals(proposedCategory.getId(), updated.getCategoryId());
        assertTrue(updated.getLabelIds().contains(proposedLabelId));
        assertEquals("Current note", updated.getNote());
    }

    @Test
    void applyingReviewSkipsDeselectedTransactions() {
        openReviewDialog();
        TableView<TransactionRulePreview> reviewTable = lookup("#ruleReviewTable").query();
        TableColumn<TransactionRulePreview, ?> includeColumn = reviewTable.getColumns().getFirst();
        assertTrue(includeColumn.getText().isBlank());
        assertEquals(36, includeColumn.getPrefWidth());
        BooleanProperty selectedTransaction = (BooleanProperty) includeColumn.getCellObservableValue(
                rowFor(reviewTable, transaction.getId()));
        interact(() -> selectedTransaction.set(false));

        Transaction includedTransaction = applicationContext.getBean(TransactionService.class)
                .createTransaction(Transaction.builder()
                        .accountId(transaction.getAccountId())
                        .originalPayee(transaction.getOriginalPayee())
                        .amount(BigDecimal.ONE)
                        .bookingDate(LocalDate.now())
                        .valueDate(LocalDate.now())
                        .categoryId(currentCategory.getId())
                        .note("Skipped note")
                        .build());
        CheckBox onlyFillMissing = lookup("#onlyFillMissingRules").query();
        interact(() -> onlyFillMissing.setSelected(false));
        assertFalse(selectedTransaction.get());
        BooleanProperty skippedSelection = (BooleanProperty) includeColumn.getCellObservableValue(
                rowFor(reviewTable, includedTransaction.getId()));

        CheckBox selectAll = lookup("#ruleReviewSelectAllCheckbox").query();
        Button applyButton = lookup("#applyReviewedRulesButton").query();
        assertTrue(selectAll.isIndeterminate());
        assertEquals(I18n.get("button.selectAll"), selectAll.getTooltip().getText());
        interact(selectAll::fire);
        assertTrue(selectedTransaction.get());
        assertTrue(skippedSelection.get());
        assertTrue(selectAll.isSelected());
        assertFalse(selectAll.isIndeterminate());
        assertEquals(I18n.get("button.deselectAll"), selectAll.getTooltip().getText());

        interact(selectAll::fire);
        assertFalse(selectedTransaction.get());
        assertFalse(skippedSelection.get());
        assertFalse(selectAll.isSelected());
        assertFalse(selectAll.isIndeterminate());
        assertTrue(applyButton.isDisabled());

        interact(selectAll::fire);
        assertTrue(skippedSelection.get());
        assertFalse(applyButton.isDisabled());
        interact(() -> selectedTransaction.set(false));
        interact(applyButton::fire);

        TransactionService transactionService = applicationContext.getBean(TransactionService.class);
        assertEquals(currentCategory.getId(), transactionService.getTransaction(transaction.getId())
                .orElseThrow().getCategoryId());
        Transaction included = transactionService.getTransaction(includedTransaction.getId()).orElseThrow();
        assertEquals(proposedCategory.getId(), included.getCategoryId());
        assertTrue(included.getLabelIds().contains(proposedLabelId));
    }

    @Test
    void reviewContextMenuActsOnSelectedRowsOrClickedRow() {
        Transaction secondTransaction = applicationContext.getBean(TransactionService.class)
                .createTransaction(Transaction.builder()
                        .accountId(transaction.getAccountId())
                        .originalPayee(transaction.getOriginalPayee())
                        .amount(BigDecimal.ONE)
                        .bookingDate(LocalDate.now())
                        .valueDate(LocalDate.now())
                        .categoryId(currentCategory.getId())
                        .note("Second review note")
                        .build());

        openReviewDialog();
        TableView<TransactionRulePreview> reviewTable = lookup("#ruleReviewTable").query();
        assertEquals(SelectionMode.MULTIPLE, reviewTable.getSelectionModel().getSelectionMode());
        BooleanProperty firstSelection = (BooleanProperty) reviewTable.getColumns().getFirst()
                .getCellObservableValue(rowFor(reviewTable, transaction.getId()));
        BooleanProperty secondSelection = (BooleanProperty) reviewTable.getColumns().getFirst()
                .getCellObservableValue(rowFor(reviewTable, secondTransaction.getId()));
        TableRow<?> firstRow = reviewRowFor(reviewTable, transaction.getId());
        TableRow<?> secondRow = reviewRowFor(reviewTable, secondTransaction.getId());
        ContextMenu contextMenu = firstRow.getContextMenu();

        assertNotNull(contextMenu);
        assertEquals(List.of(I18n.get("rules.review.selection.toggleSelection"),
                        I18n.get("rules.review.selection.select"),
                        I18n.get("rules.review.selection.deselect")), menuLabels(contextMenu));

        interact(() -> reviewTable.getSelectionModel().selectIndices(firstRow.getIndex(), secondRow.getIndex()));
        interact(menuItem(contextMenu, I18n.get("rules.review.selection.toggleSelection"))::fire);
        assertFalse(firstSelection.get());
        assertFalse(secondSelection.get());

        interact(menuItem(contextMenu, I18n.get("rules.review.selection.select"))::fire);
        assertTrue(firstSelection.get());
        assertTrue(secondSelection.get());

        interact(() -> {
            firstSelection.set(false);
            secondSelection.set(false);
            reviewTable.getSelectionModel().clearAndSelect(firstRow.getIndex());
        });
        interact(menuItem(secondRow.getContextMenu(), I18n.get("rules.review.selection.select"))::fire);
        assertFalse(firstSelection.get());
        assertTrue(secondSelection.get());
    }

    @Test
    void rulesToolbarShowsActionsWithoutToggleButton() {
        assertEquals(List.of(I18n.get("button.addRule"), I18n.get("button.moveUp"),
                        I18n.get("button.moveDown"), I18n.get("button.applyAll")),
                lookup(".view-header .button").queryAll().stream()
                        .map(node -> ((Button) node).getText()).toList());
    }

    @Test
    void rulesTableDefaultsToPrioritySortAndCanSortAndSearchByName() {
        TableView<Rule> ruleTable = lookup("#ruleTable").query();
        int totalRuleCount = ruleTable.getItems().size();
        Button clearSearchButton = lookup("#ruleSearchClearButton").query();

        assertEquals(ruleTable.getColumns().getFirst(), ruleTable.getSortOrder().getFirst());
        assertTrue(ruleTable.getColumns().stream().allMatch(TableColumnBase::isSortable));
        assertFalse(clearSearchButton.isVisible());
        assertEquals(ruleTable.getItems().stream().map(Rule::getPriority).sorted().toList(),
                ruleTable.getItems().stream().map(Rule::getPriority).toList());

        interact(() -> {
            ruleTable.getSortOrder().setAll(ruleTable.getColumns().get(1));
            ruleTable.sort();
        });
        List<String> displayedNames = ruleTable.getItems().stream().map(Rule::getName).toList();
        assertEquals(displayedNames.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), displayedNames);

        TextField searchField = lookup("#ruleSearchField").query();
        interact(() -> searchField.setText(disabledRuleName.toUpperCase(Locale.ROOT)));
        assertTrue(clearSearchButton.isVisible());
        assertEquals(List.of(disabledRuleName), ruleTable.getItems().stream().map(Rule::getName).toList());

        interact(clearSearchButton::fire);
        assertEquals("", searchField.getText());
        assertEquals(totalRuleCount, ruleTable.getItems().size());
        assertFalse(clearSearchButton.isVisible());
    }

    @Test
    void rulesTableShowsPriorityNameDisabledStyleAndPerRuleCommands() {
        TableView<Rule> ruleTable = lookup("#ruleTable").query();

        assertEquals(List.of(I18n.get("rules.column.number"), I18n.get("label.ruleName")),
                ruleTable.getColumns().stream().map(TableColumnBase::getText).toList());
        assertEquals(56, ruleTable.getColumns().getFirst().getMinWidth());
        assertEquals(56, ruleTable.getColumns().getFirst().getPrefWidth());
        assertEquals(56, ruleTable.getColumns().getFirst().getMaxWidth());

        TableRow<?> enabledRow = rowForRule(ruleTable, ruleName);
        TableRow<?> disabledRow = rowForRule(ruleTable, disabledRuleName);
        assertEquals("", enabledRow.getStyle());
        assertTrue(disabledRow.getStyleClass().contains("disabled-rule-row"));

        ContextMenu enabledMenu = enabledRow.getContextMenu();
        assertNotNull(enabledMenu);
        assertEquals(List.of(I18n.get("button.edit"), I18n.get("rules.applySelected"), I18n.get("button.moveUp"),
                        I18n.get("button.moveDown"), I18n.get("rules.disable"),
                        I18n.get("button.delete")), menuLabels(enabledMenu));

        ContextMenu disabledMenu = disabledRow.getContextMenu();
        assertNotNull(disabledMenu);
        assertEquals(List.of(I18n.get("button.edit"), I18n.get("rules.applySelected"), I18n.get("button.moveUp"),
                        I18n.get("button.moveDown"), I18n.get("rules.enable"),
                        I18n.get("button.delete")), menuLabels(disabledMenu));
        assertTrue(menuItem(disabledMenu, I18n.get("rules.applySelected")).isDisable());

        RuleService ruleService = applicationContext.getBean(RuleService.class);
        Rule enabledRule = ruleService.getAllRules().stream()
                .filter(rule -> rule.getName().equals(ruleName)).findFirst().orElseThrow();
        Rule disabledRule = ruleService.getAllRules().stream()
                .filter(rule -> rule.getName().equals(disabledRuleName)).findFirst().orElseThrow();
        int enabledPriority = enabledRule.getPriority();
        int disabledPriority = disabledRule.getPriority();
        interact(() -> menuItem(enabledMenu, I18n.get("button.moveDown")).fire());
        assertEquals(disabledPriority, ruleService.getRule(enabledRule.getId()).orElseThrow().getPriority());
        assertEquals(enabledPriority, ruleService.getRule(disabledRule.getId()).orElseThrow().getPriority());

        ContextMenu reorderedDisabledMenu = rowForRule(ruleTable, disabledRuleName).getContextMenu();
        interact(() -> menuItem(reorderedDisabledMenu, I18n.get("rules.enable")).fire());
        assertTrue(ruleService.getRule(disabledRule.getId()).orElseThrow().isEnabled());
        assertEquals("", rowForRule(ruleTable, disabledRuleName).getStyle());
    }

    @Test
    void selectingRuleShowsSummaryAndFilteredMatchingTransactions() {
        TableView<Rule> ruleTable = lookup("#ruleTable").query();
        Rule selectedRule = ruleTable.getItems().stream()
                .filter(rule -> rule.getName().equals(ruleName)).findFirst().orElseThrow();
        Transaction nonConflictingTransaction = applicationContext.getBean(TransactionService.class)
                .createTransaction(Transaction.builder().accountId(transaction.getAccountId())
                        .originalPayee(transaction.getOriginalPayee().replace("purchase", "groceries"))
                        .amount(BigDecimal.ONE).bookingDate(LocalDate.of(2025, 2, 2))
                        .valueDate(LocalDate.of(2025, 2, 2)).build());

        interact(() -> ruleTable.getSelectionModel().select(selectedRule));

        assertTrue(lookup("#ruleConditionsSummary").queryLabeled().getText().contains("REVIEW-"));
        assertTrue(lookup("#ruleActionsSummary").queryLabeled().getText().contains(proposedCategory.getName()));
        assertEquals("ALL_TIME", lookup("#datePresetFilter").<ComboBox<?>>query().getValue().toString());
        assertEquals(I18n.get("rules.matchCount", 2), lookup("#ruleMatchSummary").queryLabeled().getText());
        Rule conflictingRule = applicationContext.getBean(RuleService.class).getAllRules().stream()
                .filter(rule -> rule.getName().equals(disabledRuleName)).findFirst().orElseThrow();
        String conflictName = I18n.get("rules.ruleReference", conflictingRule.getName(), conflictingRule.getPriority());
        String selectedRuleName = I18n.get("rules.ruleReference", selectedRule.getName(), selectedRule.getPriority());
        assertEquals(I18n.get("rules.conflictCount", 1, conflictName),
                lookup("#ruleConflictSummary").queryLabeled().getText());
        TableView<Transaction> transactions = lookup("#ruleTransactionsTable").query();
        assertEquals(List.of(transaction.getId(), nonConflictingTransaction.getId()),
                transactions.getItems().stream().map(Transaction::getId).toList());
        TableColumn<Transaction, ?> matchedRuleCountColumn = transactions.getColumns().stream()
                .filter(column -> I18n.get("rules.matchingRuleCountColumn").equals(column.getText()))
                .findFirst().orElseThrow();
        TableColumn<Transaction, ?> matchedRulesColumn = transactions.getColumns().stream()
                .filter(column -> I18n.get("rules.matchingRulesColumn").equals(column.getText()))
                .findFirst().orElseThrow();
        assertEquals(48, matchedRuleCountColumn.getPrefWidth());
        assertTrue(matchedRuleCountColumn.isResizable());
        assertTrue(matchedRuleCountColumn.getMaxWidth() > matchedRuleCountColumn.getPrefWidth());
        assertTrue(matchedRuleCountColumn.isSortable());
        assertEquals(2, matchedRuleCountColumn.getCellData(transaction));
        assertEquals(1, matchedRuleCountColumn.getCellData(nonConflictingTransaction));
        assertEquals(selectedRuleName + ", " + conflictName,
                matchedRulesColumn.getCellData(transaction));
        assertEquals(selectedRuleName, matchedRulesColumn.getCellData(nonConflictingTransaction));
        interact(() -> {
            matchedRuleCountColumn.setSortType(TableColumn.SortType.DESCENDING);
            transactions.getSortOrder().setAll(matchedRuleCountColumn);
            transactions.sort();
        });
        assertEquals(List.of(transaction.getId(), nonConflictingTransaction.getId()),
                transactions.getItems().stream().map(Transaction::getId).toList());
        TableColumn<?, ?> bookingDateColumn = transactions.getColumns().stream()
                .filter(column -> I18n.get("col.bookingDate").equals(column.getText()))
                .findFirst().orElseThrow();
        TableColumn<?, ?> valueDateColumn = transactions.getColumns().stream()
                .filter(column -> I18n.get("col.valueDate").equals(column.getText()))
                .findFirst().orElseThrow();
        assertEquals(formatDate(transaction.getBookingDate()),
                cellForTransaction(transactions, bookingDateColumn, transaction).getText());
        assertEquals(formatDate(transaction.getValueDate()),
                cellForTransaction(transactions, valueDateColumn, transaction).getText());

        TextField search = lookup("#searchField").query();
        interact(() -> search.setText("not a matching payee"));
        assertTrue(transactions.getItems().isEmpty());
        interact(() -> search.setText("REVIEW-"));
        assertEquals(List.of(transaction.getId(), nonConflictingTransaction.getId()),
                transactions.getItems().stream().map(Transaction::getId).toList());
    }

    @Test
    void editingRuleFromContextMenuUpdatesRuleAndSelectedSummary() {
        TableView<Rule> ruleTable = lookup("#ruleTable").query();
        Rule selectedRule = ruleTable.getItems().stream()
                .filter(rule -> rule.getName().equals(ruleName)).findFirst().orElseThrow();
        TableRow<?> row = rowForRule(ruleTable, ruleName);
        String editedCondition = "EDITED-" + System.nanoTime();
        applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                .accountId(transaction.getAccountId()).originalPayee(editedCondition).amount(BigDecimal.ONE)
                .bookingDate(LocalDate.of(2025, 2, 3)).valueDate(LocalDate.of(2025, 2, 3)).build());

        interact(() -> ruleTable.getSelectionModel().select(selectedRule));
        Platform.runLater(() -> menuItem(row.getContextMenu(), I18n.get("button.edit")).fire());
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane editorDialogPane = lookup(".dialog-pane").query();
        assertTrue(((Stage) editorDialogPane.getScene().getWindow()).isResizable());
        TextField nameField = lookup("#ruleNameField").query();
        String editedName = ruleName + " updated";
        interact(() -> nameField.setText(editedName));
        TextField conditionValue = lookup(".rule-condition-row .text-field").query();
        ComboBox<String> matchMode = lookup("#conditionMatchModeCombo").query();
        interact(() -> {
            conditionValue.setText(editedCondition);
            matchMode.setValue(I18n.get("ruleEditor.matchMode.any"));
        });
        Button saveButton = lookup("#editRuleSave").query();
        assertFalse(saveButton.isDisabled());
        interact(saveButton::fire);
        WaitForAsyncUtils.waitForFxEvents();

        Rule updatedRule = applicationContext.getBean(RuleService.class)
                .getRule(selectedRule.getId()).orElseThrow();
        assertEquals(editedName, updatedRule.getName());
        assertEquals(org.fintrax.model.RuleMatchMode.ANY, updatedRule.getMatchMode());
        assertTrue(lookup("#ruleNameSummary").queryLabeled().getText().contains(editedName));
        assertTrue(lookup("#ruleConditionsSummary").queryLabeled().getText()
                .startsWith(I18n.get("rules.summary.conditionsAny", "")));
        assertEquals(I18n.get("rules.matchCount", 1), lookup("#ruleMatchSummary").queryLabeled().getText());
        assertEquals("", lookup("#ruleConflictSummary").queryLabeled().getText());
        assertFalse(lookup("#ruleConflictSummary").query().isVisible());
    }

    @Test
    void hoveringRegexOperatorShowsGuideInSettingsLanguage() {
        SettingsService settingsService = applicationContext.getBean(SettingsService.class);
        String originalLanguage = settingsService.getLanguage();
        String editMenuItemText = I18n.get("button.edit");
        try {
            interact(() -> {
                settingsService.saveLanguage("de");
                I18n.setLocale(settingsService.getLocale());
            });

            TableView<Rule> ruleTable = lookup("#ruleTable").query();
            Rule selectedRule = ruleTable.getItems().stream()
                    .filter(rule -> rule.getName().equals(ruleName)).findFirst().orElseThrow();
            TableRow<?> row = rowForRule(ruleTable, ruleName);
            interact(() -> ruleTable.getSelectionModel().select(selectedRule));
            Platform.runLater(() -> menuItem(row.getContextMenu(), editMenuItemText).fire());
            WaitForAsyncUtils.waitForFxEvents();

            ComboBox<String> operator = lookup(".rule-condition-operator").query();
            moveTo("#ruleNameField");
            interact(() -> operator.setValue(I18n.get("ruleEditor.operator.matchesRegex")));
            assertFalse(lookup(".regex-help-guide").query().isVisible());

            moveTo(operator);
            WaitForAsyncUtils.waitForFxEvents();
            assertTrue(lookup(".regex-help-guide").query().isVisible());
            assertEquals("Regex-Kurzübersicht", lookup(".regex-help-title").queryLabeled().getText());
            assertTrue(lookup(".regex-help-example").queryLabeled().getText().contains(".*Miete.*"));
            assertTrue(lookup(".regex-help-example").queryLabeled().getText().contains("Monatliche Mietzahlung"));

            moveTo("#ruleNameField");
            WaitForAsyncUtils.waitForFxEvents();
            assertFalse(lookup(".regex-help-guide").query().isVisible());
        } finally {
            interact(() -> {
                settingsService.saveLanguage(originalLanguage);
                I18n.setLocale(settingsService.getLocale());
            });
        }
    }

    @Test
    void creatingMergedRuleRejectsCaseInsensitiveDuplicateNameInEditor() {
        TableView<Rule> ruleTable = lookup("#ruleTable").query();
        openMergeRuleEditor(ruleTable);

        TextField nameField = lookup("#ruleNameField").query();
        interact(() -> nameField.setText(ruleName.toUpperCase(Locale.ROOT)));

        assertTrue(lookup("#createRuleSave").queryButton().isDisabled());
        assertTrue(lookup("#ruleNameErrorLabel").query().isVisible());
        interact(lookup("#createRuleCancel").queryButton()::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void creatingMergedRuleExplainsMultipleCategoryActionConflict() {
        TableView<Rule> ruleTable = lookup("#ruleTable").query();
        openMergeRuleEditor(ruleTable, ruleName, disabledRuleName);

        assertTrue(lookup("#createRuleSave").queryButton().isDisabled());
        assertTrue(lookup("#ruleActionsErrorLabel").query().isVisible());
        interact(lookup("#createRuleCancel").queryButton()::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void creatingMergedRuleMovesPriorityBeforeSelectedRulesInTable() {
        TableView<Rule> ruleTable = lookup("#ruleTable").query();
        RuleService ruleService = applicationContext.getBean(RuleService.class);
        Rule sourceOne = ruleService.getAllRules().stream()
                .filter(rule -> rule.getName().equals(mergeSourceOneName)).findFirst().orElseThrow();
        Rule sourceTwo = ruleService.getAllRules().stream()
                .filter(rule -> rule.getName().equals(mergeSourceTwoName)).findFirst().orElseThrow();
        int expectedPriority = sourceOne.getPriority();
        TableColumn<Rule, ?> ruleNameColumn = ruleTable.getColumns().get(1);
        interact(() -> {
            ruleNameColumn.setSortType(TableColumn.SortType.ASCENDING);
            ruleTable.getSortOrder().setAll(ruleNameColumn);
            ruleTable.sort();
        });
        TextField searchField = lookup("#ruleSearchField").query();
        interact(() -> searchField.setText("Merge source"));
        openMergeRuleEditor(ruleTable);

        String mergedName = "ZZZ merged " + System.nanoTime();
        TextField nameField = lookup("#ruleNameField").query();
        interact(() -> nameField.setText(mergedName));
        interact(lookup("#createRuleSave").queryButton()::fire);
        WaitForAsyncUtils.waitForFxEvents();

        Rule mergedRule = ruleService.getAllRules().stream()
                .filter(rule -> rule.getName().equals(mergedName)).findFirst().orElseThrow();
        assertEquals(expectedPriority, mergedRule.getPriority());
        assertEquals(ruleTable.getColumns().getFirst(), ruleTable.getSortOrder().getFirst());
        assertTrue(ruleTable.getItems().indexOf(mergedRule) < ruleTable.getItems().indexOf(sourceOne));
        assertTrue(ruleTable.getItems().indexOf(mergedRule) < ruleTable.getItems().indexOf(sourceTwo));
        assertEquals("", searchField.getText());
        assertEquals(List.of(mergedRule.getId()), ruleTable.getSelectionModel().getSelectedItems().stream()
                .map(Rule::getId).toList());
    }

    @Test
    void creatingMergedRulePreservesAmountRangeBounds() {
        TableView<Rule> ruleTable = lookup("#ruleTable").query();
        openMergeRuleEditor(ruleTable, mergeSourceOneName, mergeAmountSourceName);

        String mergedName = "Merged amount rule " + System.nanoTime();
        TextField nameField = lookup("#ruleNameField").query();
        interact(() -> nameField.setText(mergedName));
        assertFalse(lookup("#createRuleSave").queryButton().isDisabled());
        interact(lookup("#createRuleSave").queryButton()::fire);
        WaitForAsyncUtils.waitForFxEvents();

        Rule mergedRule = applicationContext.getBean(RuleService.class).getAllRules().stream()
                .filter(rule -> rule.getName().equals(mergedName)).findFirst().orElseThrow();
        RuleCondition amountRange = mergedRule.getConditions().stream()
                .filter(condition -> condition.getOperator() == Operator.AMOUNT_RANGE)
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("1234.56"), amountRange.getAmountMin());
        assertEquals(new BigDecimal("1234.57"), amountRange.getAmountMax());
    }

    @Test
    void mergingOneSelectedRuleIntoAnExistingRuleIsAvailable() {
        TableView<Rule> ruleTable = lookup("#ruleTable").query();
        RuleService ruleService = applicationContext.getBean(RuleService.class);
        Rule sourceRule = ruleService.getAllRules().stream()
                .filter(rule -> rule.getName().equals(mergeSourceOneName)).findFirst().orElseThrow();
        Rule targetRule = ruleService.getAllRules().stream()
                .filter(rule -> rule.getName().equals(mergeSourceTwoName)).findFirst().orElseThrow();
        interact(() -> {
            ruleTable.getSelectionModel().clearSelection();
            ruleTable.getSelectionModel().select(sourceRule);
        });

        TableRow<?> row = rowForRule(ruleTable, mergeSourceOneName);
        ContextMenu contextMenu = row.getContextMenu();
        interact(() -> contextMenu.show(row, Side.RIGHT, 0, 0));
        WaitForAsyncUtils.waitForFxEvents();
        MenuItem mergeItem = menuItem(contextMenu, I18n.get("rules.mergeSelected"));
        assertFalse(mergeItem.isDisable());
        interact(mergeItem::fire);
        WaitForAsyncUtils.waitForFxEvents();

        RadioButton newRuleOption = lookup("#mergeIntoNewRule").query();
        RadioButton existingRuleOption = lookup("#mergeIntoExistingRule").query();
        assertTrue(newRuleOption.isDisable());
        assertTrue(existingRuleOption.isSelected());
        assertTrue(lookup("#deleteSourceRulesCheckBox").<CheckBox>query().isSelected());
        Button targetSelector = lookup("#mergeTargetRuleButton").query();
        interact(targetSelector::fire);
        TextField targetSearch = lookup("#pickerSearch").query();
        ListView<Rule> targetOptions = lookup("#pickerList").query();
        interact(() -> targetSearch.setText(targetRule.getName()));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(targetRule, targetOptions.getItems().getFirst());
        interact(() -> Event.fireEvent(targetSearch, new KeyEvent(KeyEvent.KEY_PRESSED,
                "", "", KeyCode.ENTER, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
        interact(lookup("#mergeRulesContinue").queryButton()::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(lookup("#editRuleSave").queryButton().isDisabled());
        interact(lookup("#editRuleSave").queryButton()::fire);
        WaitForAsyncUtils.waitForFxEvents();

        Rule mergedTarget = ruleService.getRule(targetRule.getId()).orElseThrow();
        assertEquals(2, mergedTarget.getConditions().size());
        assertEquals(2, mergedTarget.getActions().size());
        assertTrue(ruleService.getRule(sourceRule.getId()).isEmpty());
    }

    private void openMergeRuleEditor(TableView<Rule> ruleTable) {
        openMergeRuleEditor(ruleTable, mergeSourceOneName, mergeSourceTwoName);
    }

    private void openMergeRuleEditor(TableView<Rule> ruleTable, String... sourceNames) {
        List<Rule> sourceRules = java.util.Arrays.stream(sourceNames)
                .map(name -> ruleTable.getItems().stream()
                        .filter(rule -> rule.getName().equals(name)).findFirst().orElseThrow())
                .toList();
        interact(() -> {
            ruleTable.getSelectionModel().clearSelection();
            sourceRules.forEach(ruleTable.getSelectionModel()::select);
        });
        TableRow<?> row = rowForRule(ruleTable, sourceNames[0]);
        Platform.runLater(() -> menuItem(row.getContextMenu(), I18n.get("rules.mergeSelected")).fire());
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> lookup("#deleteSourceRulesCheckBox").<CheckBox>query().setSelected(false));
        interact(lookup("#mergeRulesContinue").queryButton()::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private TransactionRulePreview rowFor(TableView<TransactionRulePreview> table, Long transactionId) {
        return table.getItems().stream()
                .filter(row -> row.transactionId().equals(transactionId))
                .findFirst().orElseThrow();
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale()));
    }

    private TableCell<?, ?> cellForTransaction(TableView<Transaction> table, TableColumn<?, ?> column,
                                               Transaction transaction) {
        return table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == column
                        && cell.getTableRow() != null && cell.getTableRow().getItem() instanceof Transaction row
                        && transaction.getId().equals(row.getId()))
                .findFirst()
                .orElseThrow();
    }

    private TableRow<?> reviewRowFor(TableView<TransactionRulePreview> table, Long transactionId) {
        return table.lookupAll(".table-row-cell").stream()
                .filter(node -> node instanceof TableRow<?> row && !row.isEmpty()
                        && row.getItem() instanceof TransactionRulePreview preview
                        && preview.transactionId().equals(transactionId))
                .map(node -> (TableRow<?>) node)
                .findFirst().orElseThrow();
    }

    private void openReviewDialog() {
        Button applyRulesButton = lookup("#applyAllButton").query();
        Platform.runLater(applyRulesButton::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private TableRow<?> rowForRule(TableView<Rule> table, String name) {
        Rule rule = table.getItems().stream()
                .filter(item -> item.getName().equals(name)).findFirst().orElseThrow();
        interact(() -> table.scrollTo(rule));
        WaitForAsyncUtils.waitForFxEvents();
        return table.lookupAll(".table-row-cell").stream()
                .filter(node -> node instanceof TableRow<?> row && !row.isEmpty()
                        && row.getItem() instanceof Rule rowRule && rowRule.getName().equals(name))
                .map(node -> (TableRow<?>) node)
                .findFirst().orElseThrow();
    }

    private List<String> menuLabels(ContextMenu contextMenu) {
        return contextMenu.getItems().stream().map(MenuItem::getText)
                .filter(text -> text != null).toList();
    }

    private MenuItem menuItem(ContextMenu contextMenu, String text) {
        return contextMenu.getItems().stream()
                .filter(item -> text.equals(item.getText()))
                .findFirst().orElseThrow();
    }
}
