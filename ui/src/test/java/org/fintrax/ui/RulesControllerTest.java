package org.fintrax.ui;

import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.RuleService;
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
    private String ruleName;
    private String disabledRuleName;

    @Override
    public void start(Stage stage) throws Exception {
        AccountService accountService = applicationContext.getBean(AccountService.class);
        CategoryService categoryService = applicationContext.getBean(CategoryService.class);
        RuleService ruleService = applicationContext.getBean(RuleService.class);
        TransactionService transactionService = applicationContext.getBean(TransactionService.class);

        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Review Bank", "User", AccountType.GIRO, null);
        String suffix = Long.toString(System.nanoTime());
        currentCategory = categoryService.createCategory("Review Current " + suffix, null, null);
        proposedCategory = categoryService.createCategory("Review Proposed " + suffix, null, null);
        ruleName = "Review rule " + suffix;
        ruleService.createRule(ruleName, List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME).operator(Operator.CONTAINS).value("REVIEW-" + suffix).build()),
                List.of(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                                .categoryId(proposedCategory.getId()).build(),
                        RuleAction.builder().actionType(ActionType.SET_NOTE).note("Proposed note").build()));
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
        TransactionRulePreview initialRow = rowFor(reviewTable, transaction.getId());
        assertEquals(currentCategory.getId(), initialRow.proposedCategoryId());

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
        assertEquals("Proposed note", updated.getNote());
    }

    @Test
    void enabledRuleContextMenuReviewsSelectedRuleAndDisabledRuleHasNoApplyAction() {
        ListView<?> ruleList = lookup("#ruleList").query();
        ListCell<?> enabledCell = cellForRule(ruleList, ruleName);
        ContextMenu contextMenu = enabledCell.getContextMenu();
        assertNotNull(contextMenu);
        MenuItem applySelected = contextMenu.getItems().get(0);
        assertEquals(I18n.get("rules.applySelected"), applySelected.getText());

        ListCell<?> disabledCell = cellForRule(ruleList, disabledRuleName);
        assertNull(disabledCell.getContextMenu());
    }

    private TransactionRulePreview rowFor(TableView<TransactionRulePreview> table, Long transactionId) {
        return table.getItems().stream()
                .filter(row -> row.transactionId().equals(transactionId))
                .findFirst().orElseThrow();
    }

    private ListCell<?> cellForRule(ListView<?> list, String name) {
        return list.lookupAll(".list-cell").stream()
                .filter(node -> node instanceof ListCell<?> cell && cell.getText() != null
                        && cell.getText().contains(name))
                .map(node -> (ListCell<?>) node)
                .findFirst().orElseThrow();
    }
}
