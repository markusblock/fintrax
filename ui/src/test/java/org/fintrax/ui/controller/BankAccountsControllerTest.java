package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.AccountService;
import org.fintrax.service.BankService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.RuleService;
import org.fintrax.service.SyncProgress;
import org.fintrax.service.SyncResult;
import org.fintrax.service.TransactionRulePreview;
import org.fintrax.service.TransactionService;
import org.fintrax.ui.AbstractUITest;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.ViewLoader;
import org.kordamp.ikonli.javafx.FontIcon;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        FintraxUI.configureScene(scene);
        FintraxUI.applyTheme(scene, "light");
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void accountCardsUseDarkThemeSurfaceAndSecondaryTextColors() {
        interact(() -> {
            VBox card = lookup(".account-card").query();
            FintraxUI.applyTheme(card.getScene(), "dark");
            card.getScene().getRoot().applyCss();

            BackgroundFill cardFill = card.getBackground().getFills().get(0);
            assertEquals(Color.web("#0d1117"), cardFill.getFill());

            Label holderLabel = lookup(".account-holder").query();
            assertEquals(Color.web("#8b949e"), holderLabel.getTextFill());
        });
    }

    @Test
    void accountActionButtonsShowOnlyIconsAndRemainAccessible() {
        List<Button> actionButtons = List.of(
                lookup(".sync-button").query(),
                lookup("#editAccountButton-" + account.getId()).query(),
                lookup(".delete-button").query());

        for (Button button : actionButtons) {
            assertTrue(button.getText().isBlank());
            assertNotNull(button.getGraphic());
            assertNotNull(button.getTooltip());
            assertFalse(button.getAccessibleText().isBlank());
            assertEquals(button.getAccessibleText(), button.getTooltip().getText());
        }
    }

    @Test
    void editingAccountUpdatesDetailsAndPreservesIdentityAndImportedState() {
        AccountService accountService = applicationContext.getBean(AccountService.class);
        LocalDateTime balanceDate = LocalDateTime.of(2025, 1, 2, 3, 4);
        LocalDateTime syncDate = LocalDateTime.of(2025, 2, 3, 4, 5);
        accountService.updateBalance(account.getId(), new BigDecimal("42.50"), balanceDate);
        accountService.updateLastSync(account.getId(), syncDate);
        LocalDateTime createdAt = account.getCreatedAt();
        BankService bankService = applicationContext.getBean(BankService.class);

        clickOn("#editAccountButton-" + account.getId());

        TextField ibanField = lookup("#ibanField").query();
        TextField currencyField = lookup("#currencyField").query();
        assertFalse(ibanField.isEditable());
        assertFalse(currencyField.isEditable());
        interact(() -> {
            lookup("#bankNameField").<TextField>query().setText("Updated Bank");
            lookup("#bicField").<TextField>query().setText("NEWBIC");
            lookup("#accountNameField").<TextField>query().setText("Updated account");
            lookup("#accountHolderField").<TextField>query().setText("Updated holder");
            lookup("#accountTypeCombo").<ComboBox<AccountType>>query().setValue(AccountType.SAVINGS);
            lookup("#accountCommentField").<TextArea>query().setText("Updated comment");
        });
        clickOn("#saveAccountButton");
        WaitForAsyncUtils.waitForFxEvents();

        BankAccount updated = accountService.getAccount(account.getId()).orElseThrow();
        assertEquals("Updated account", updated.getAccountName());
        assertEquals("Updated Bank", bankService.getBankForAccount(updated).orElseThrow().getName());
        assertEquals("NEWBIC", bankService.getBankForAccount(updated).orElseThrow().getBic());
        assertEquals("Updated holder", updated.getAccountHolder());
        assertEquals(AccountType.SAVINGS, updated.getAccountType());
        assertEquals("Updated comment", updated.getComment());
        assertEquals(account.getId(), updated.getId());
        assertEquals("DE89370400440532013000", updated.getIban());
        assertEquals("EUR", updated.getCurrency());
        assertEquals(new BigDecimal("42.50"), updated.getBalance());
        assertEquals(balanceDate, updated.getBalanceDate());
        assertEquals(syncDate, updated.getLastSyncAt());
        assertEquals(createdAt, updated.getCreatedAt());
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

        Button applyRules = lookup("#applyRulesToNewTransactions").query();
        assertTrue(applyRules.isDisabled());
        clickOn(lookup("#syncReportCloseButton").<Button>query());
    }

    @Test
    void syncProgressDialogShowsStagesAndAdvancesStatusIcons() {
        SyncProgressDialog.Account target = new SyncProgressDialog.Account(account.getId(),
                "Sync Bank · Girokonto", "•••• 3000");
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<SyncProgressDialog> progressDialogRef = new AtomicReference<>();

        interact(() -> {
            Window owner = lookup(".account-card").query().getScene().getWindow();
            SyncProgressDialog progressDialog = new SyncProgressDialog(owner);
            progressDialog.setCancelAction(() -> cancelled.set(true));
            progressDialog.show(List.of(target));
            progressDialogRef.set(progressDialog);
        });
        SyncProgressDialog progressDialog = progressDialogRef.get();
        WaitForAsyncUtils.waitForFxEvents();

        Stage dialogStage = Window.getWindows().stream()
                .filter(Stage.class::isInstance)
                .map(Stage.class::cast)
                .filter(Stage::isShowing)
                .filter(stage -> stage.getScene().getRoot().lookup(".sync-progress-account-card") != null)
                .findFirst().orElseThrow();
        Parent dialogRoot = dialogStage.getScene().getRoot();
        assertTrue(dialogStage.isShowing());
        assertEquals(I18n.get("message.sync.progress.overview.single", 1, 0, 0),
                ((Label) dialogRoot.lookup("#syncProgressOverview")).getText());
        assertFalse(((Button) dialogRoot.lookup("#syncReportCloseButton")).isDisabled());
        assertTrue(((Button) dialogRoot.lookup("#viewSyncActivityLogButton")).isDisabled());
        assertTrue(((Button) dialogRoot.lookup("#applyRulesToNewTransactions")).isDisabled());
        assertEquals(I18n.get("sync.progressStage.CONNECTION"), ((Label) dialogRoot.lookup(
                "#syncProgressStage_" + account.getId() + "_CONNECTION")).getText());
        for (SyncStage stage : List.of(SyncStage.CONNECTION, SyncStage.AUTHENTICATION,
                SyncStage.DATA_RETRIEVAL, SyncStage.DATA_IMPORT)) {
            assertEquals("fth-circle", ((FontIcon) dialogRoot.lookup(
                    "#syncProgressStatus_" + account.getId() + "_" + stage.name()))
                    .getIconLiteral());
        }

        progressDialog.updateProgress(new SyncProgress(account.getId(), SyncStage.CONNECTION, 0, 0, 0));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(dialogRoot.lookup("#syncProgressSpinner_" + account.getId() + "_CONNECTION").isVisible());
        progressDialog.updateProgress(new SyncProgress(account.getId(), SyncStage.AUTHENTICATION, 0, 0, 0));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("fth-check-circle", ((FontIcon) dialogRoot.lookup(
                "#syncProgressStatus_" + account.getId() + "_CONNECTION"))
                .getIconLiteral());
        progressDialog.updateProgress(new SyncProgress(account.getId(), SyncStage.DATA_RETRIEVAL, 12, 0, 0));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(I18n.get("message.sync.progress.downloaded", 12),
                ((Label) dialogRoot.lookup("#syncProgressResult_" + account.getId() + "_DATA_RETRIEVAL"))
                        .getText());
        progressDialog.updateProgress(new SyncProgress(account.getId(), SyncStage.DATA_IMPORT, 12, 9, 3));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(I18n.get("message.sync.progress.imported", 9, 12, 3),
                ((Label) dialogRoot.lookup("#syncProgressResult_" + account.getId() + "_DATA_IMPORT"))
                        .getText());

        SyncLog syncLog = SyncLog.builder().bankAccountId(account.getId()).status(SyncStatus.SUCCESS)
                .newCount(9).skippedCount(3).build();
        progressDialog.finishAccount(new SyncResult(syncLog, List.of()));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(I18n.get("message.sync.progress.overview.single", 1, 1, 0),
                ((Label) dialogRoot.lookup("#syncProgressOverview")).getText());
        for (SyncStage stage : List.of(SyncStage.CONNECTION, SyncStage.AUTHENTICATION,
                SyncStage.DATA_RETRIEVAL, SyncStage.DATA_IMPORT)) {
            assertEquals("fth-check-circle", ((FontIcon) dialogRoot.lookup(
                    "#syncProgressStatus_" + account.getId() + "_" + stage.name()))
                    .getIconLiteral());
        }
        assertFalse(dialogRoot.lookup("#syncProgressAccountBody_" + account.getId()).isVisible());
        clickOn("#syncReportCloseButton");
        assertTrue(cancelled.get());
        assertFalse(dialogStage.isShowing());
    }

    private Transaction transaction(String checksum, String payee) {
        return applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                .accountId(account.getId()).checksum(checksum).originalPayee(payee).amount(BigDecimal.TEN)
                .bookingDate(LocalDate.now()).valueDate(LocalDate.now()).build());
    }
}
