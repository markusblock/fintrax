package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.fintrax.model.*;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.RuleService;
import org.fintrax.service.SyncResult;
import org.fintrax.service.TransactionRulePreview;
import org.fintrax.service.TransactionService;
import org.fintrax.ui.AbstractUITest;
import org.fintrax.ui.ViewLoader;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankAccountsControllerTest extends AbstractUITest {
    private BankAccountsController controller;
    private Transaction newTransaction;
    private Transaction olderTransaction;
    private BankAccount account;
    private BankAccount failedAccount;

    @Override
    public void start(Stage stage) throws Exception {
        AccountService accountService = applicationContext.getBean(AccountService.class);
        CategoryService categoryService = applicationContext.getBean(CategoryService.class);
        RuleService ruleService = applicationContext.getBean(RuleService.class);
        account = accountService.createAccount("DE89370400440532013000", null,
                "Sync Bank", "User", AccountType.GIRO, null);
        failedAccount = accountService.createAccount("DE11111111111111111111", null,
                "Offline Bank", "User", AccountType.GIRO, null);
        Category category = categoryService.createCategory("Sync Category " + System.nanoTime(), null, null);
        ruleService.createRule("Sync Rule " + System.nanoTime(), List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME).operator(Operator.CONTAINS).value("SYNC").build()),
                List.of(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(category.getId()).build()));
        olderTransaction = transaction("old-sync", "SYNC old");
        newTransaction = transaction("new-sync", "SYNC new");

        Parent root = applicationContext.getBean(ViewLoader.class).load("bankAccounts");
        controller = (BankAccountsController) root.getProperties().get(BankAccountsController.class.getName());
        Scene scene = new Scene(root, 1024, 768);
        scene.getStylesheets().add(getClass().getResource("/css/fintrax.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void accountCardsUseDarkThemeSurfaceAndSecondaryTextColors() {
        interact(() -> {
            VBox card = lookup(".account-card").query();
            card.getScene().getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());
            card.getScene().getRoot().applyCss();

            BackgroundFill cardFill = card.getBackground().getFills().get(0);
            assertEquals(Color.web("#303030"), cardFill.getFill());

            Label holderLabel = lookup(".account-holder").query();
            assertEquals(Color.web("#b0b0b0"), holderLabel.getTextFill());
        });
    }

    @Test
    void syncReportReviewsOnlyNewTransactions() {
        SyncLog syncLog = SyncLog.builder().bankAccountId(account.getId()).status(SyncStatus.SUCCESS)
                .newCount(1).skippedCount(1).build();
        SyncLog failedSyncLog = SyncLog.builder().bankAccountId(failedAccount.getId()).status(SyncStatus.FAILED)
                .errorMessage("bank unavailable").build();
        SyncResult successfulResult = new SyncResult(syncLog, List.of(newTransaction.getId()));
        SyncResult failedResult = new SyncResult(failedSyncLog, List.of());
        assertNotEquals(olderTransaction.getId(), newTransaction.getId());
        assertEquals(List.of(olderTransaction.getId(), newTransaction.getId()),
                applicationContext.getBean(TransactionService.class).getAllTransactions().stream()
                        .filter(transaction -> transaction.getId().equals(olderTransaction.getId())
                                || transaction.getId().equals(newTransaction.getId()))
                        .map(Transaction::getId).toList());
        assertEquals(1, applicationContext.getBean(RuleService.class)
                .previewRulesForTransactions(successfulResult.newTransactionIds(), true)
                .matchingTransactions().size());
        Platform.runLater(() -> controller.showSyncReport(List.of(successfulResult, failedResult), Map.of()));
        WaitForAsyncUtils.waitForFxEvents();

        Button applyRules = lookup("#applyRulesToNewTransactions").query();
        clickOn(applyRules);

        TableView<TransactionRulePreview> reviewTable = lookup("#ruleReviewTable").query();
        assertEquals(List.of(newTransaction.getId()), reviewTable.getItems().stream()
                .map(TransactionRulePreview::transactionId).toList());
        clickOn(lookup("#cancelRuleReviewButton").<Button>query());

        Transaction unchanged = applicationContext.getBean(TransactionService.class)
                .getTransaction(newTransaction.getId()).orElseThrow();
        assertNull(unchanged.getCategoryId());
        assertNull(applicationContext.getBean(TransactionService.class)
                .getTransaction(olderTransaction.getId()).orElseThrow().getCategoryId());
    }

    @Test
    void syncReportWithoutNewTransactionsDoesNotOfferRuleReview() {
        SyncLog syncLog = SyncLog.builder().bankAccountId(account.getId()).status(SyncStatus.SUCCESS)
                .newCount(0).skippedCount(1).build();
        SyncResult result = new SyncResult(syncLog, List.of());
        Platform.runLater(() -> controller.showSyncReport(List.of(result), Map.of()));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(lookup("#applyRulesToNewTransactions").queryAll().isEmpty());
        clickOn(lookup("#syncReportCloseButton").<Button>query());
    }

    private Transaction transaction(String checksum, String payee) {
        return applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                .accountId(account.getId()).checksum(checksum).originalPayee(payee).amount(BigDecimal.TEN)
                .bookingDate(LocalDate.now()).valueDate(LocalDate.now()).build());
    }
}
