package org.fintrax.ui;

import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulesControllerTest extends AbstractUITest {
    private Transaction transaction;
    private Category currentCategory;
    private Category proposedCategory;
    private Long proposedLabelId;
    private String ruleName;
    private String disabledRuleName;

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
        currentCategory = categoryService.createCategory("Review Current " + suffix, null, null);
        proposedCategory = categoryService.createCategory("Review Proposed " + suffix, null, null);
        proposedLabelId = labelService.createLabel("Review label " + suffix, null, null).getId();
        ruleName = "Review rule " + suffix;
        ruleService.createRule(ruleName, List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME).operator(Operator.CONTAINS).value("REVIEW-" + suffix).build()),
                List.of(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                                .categoryId(proposedCategory.getId()).build(),
                        RuleAction.builder().actionType(ActionType.ADD_LABEL)
                                .labelId(proposedLabelId).build()));
        disabledRuleName = "Disabled review rule " + suffix;
        Rule disabledRule = ruleService.createRule(disabledRuleName, List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME).operator(Operator.CONTAINS).value("REVIEW-" + suffix).build()),
                List.of(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(proposedCategory.getId()).build()));
        ruleService.toggleRule(disabledRule.getId());
        transaction = transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("REVIEW-" + suffix + " purchase").amount(BigDecimal.TEN)
                .bookingDate(LocalDate.now()).valueDate(LocalDate.now())
                .categoryId(currentCategory.getId()).note("Current note").build());

        Parent root = applicationContext.getBean(ViewLoader.class).load("rules");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void applyingRulesShowsReviewAndCancelLeavesTransactionsUnchanged() {
        Button applyRulesButton = lookup("#applyAllButton").query();
        clickOn(applyRulesButton);

        CheckBox onlyFillMissing = lookup("#onlyFillMissingRules").query();
        assertTrue(onlyFillMissing.isSelected());
        TableView<TransactionRulePreview> reviewTable = lookup("#ruleReviewTable").query();
        MenuButton reviewColumnMenu = lookup("#ruleReviewColumnMenuButton").query();
        assertSame(reviewColumnMenu, reviewTable.getColumns().getLast().getGraphic());
        assertEquals(10, reviewColumnMenu.getItems().size());
        assertEquals(I18n.get("button.columns.reset"), reviewColumnMenu.getItems().getLast().getText());
        TransactionRulePreview initialRow = rowFor(reviewTable, transaction.getId());
        assertEquals(currentCategory.getId(), initialRow.proposedCategoryId());
        assertTrue(initialRow.proposedLabelIds().contains(proposedLabelId));

        var originalReviewColumnOrder = List.copyOf(reviewTable.getColumns());
        CheckMenuItem noteItem = (CheckMenuItem) reviewColumnMenu.getItems().get(6);
        try {
            interact(() -> noteItem.setSelected(false));
            assertFalse(reviewTable.getColumns().get(6).isVisible());
            assertTrue(applicationContext.getBean(SettingsService.class)
                    .getSettingValue("rules.review.columns").contains("note=false"));
            interact(() -> {
                var dateColumn = reviewTable.getColumns().remove(0);
                reviewTable.getColumns().add(1, dateColumn);
            });
            assertEquals("payee,date,amount,account,category,labels,note,matchedRules",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("rules.review.columns.order"));
        } finally {
            interact(() -> {
                noteItem.setSelected(true);
                reviewTable.getColumns().setAll(originalReviewColumnOrder);
            });
        }

        clickOn(onlyFillMissing);
        assertFalse(onlyFillMissing.isSelected());
        assertEquals(proposedCategory.getId(), rowFor(reviewTable, transaction.getId()).proposedCategoryId());

        Button cancelButton = lookup("#cancelRuleReviewButton").query();
        clickOn(cancelButton);

        Transaction unchanged = applicationContext.getBean(TransactionService.class)
                .getTransaction(transaction.getId()).orElseThrow();
        assertNotEquals(proposedCategory.getId(), unchanged.getCategoryId());
        assertEquals("Current note", unchanged.getNote());
    }

    @Test
    void confirmingReviewAppliesProposedChanges() {
        Button applyRulesButton = lookup("#applyAllButton").query();
        clickOn(applyRulesButton);
        CheckBox onlyFillMissing = lookup("#onlyFillMissingRules").query();
        clickOn(onlyFillMissing);

        Button applyButton = lookup("#applyReviewedRulesButton").query();
        clickOn(applyButton);

        Transaction updated = applicationContext.getBean(TransactionService.class)
                .getTransaction(transaction.getId()).orElseThrow();
        assertEquals(proposedCategory.getId(), updated.getCategoryId());
        assertTrue(updated.getLabelIds().contains(proposedLabelId));
        assertEquals("Current note", updated.getNote());
    }

    @Test
    void rulesToolbarShowsActionsWithoutToggleButton() {
        assertEquals(List.of(I18n.get("button.addRule"), I18n.get("button.moveUp"),
                        I18n.get("button.moveDown"), I18n.get("button.applyAll")),
                lookup(".view-header .button").queryAll().stream()
                        .map(node -> ((Button) node).getText()).toList());
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
        assertEquals(List.of(I18n.get("rules.applySelected"), I18n.get("button.moveUp"),
                        I18n.get("button.moveDown"), I18n.get("rules.disable"),
                        I18n.get("button.delete")), menuLabels(enabledMenu));

        ContextMenu disabledMenu = disabledRow.getContextMenu();
        assertNotNull(disabledMenu);
        assertEquals(List.of(I18n.get("rules.applySelected"), I18n.get("button.moveUp"),
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

    private TransactionRulePreview rowFor(TableView<TransactionRulePreview> table, Long transactionId) {
        return table.getItems().stream()
                .filter(row -> row.transactionId().equals(transactionId))
                .findFirst().orElseThrow();
    }

    private TableRow<?> rowForRule(TableView<Rule> table, String name) {
        return table.lookupAll(".table-row-cell").stream()
                .filter(node -> node instanceof TableRow<?> row && !row.isEmpty()
                        && row.getItem() instanceof Rule rule && rule.getName().equals(name))
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
