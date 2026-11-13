package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.DiscoveredBankAccount;
import org.fintrax.model.ActivityAction;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;
import org.fintrax.model.EntityType;
import org.fintrax.service.ActivityLogger;
import org.fintrax.service.AccountService;
import org.fintrax.service.BankService;
import org.fintrax.ui.FintraxUI;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
abstract class BankAccountDialogBase {
    private static final String ACTIVITY_DIAGNOSTICS_MARKER = "\n[FINTRAX_SYNC_DIAGNOSTICS]\n";
    private static final DateTimeFormatter DIAGNOSTIC_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    protected static final String WARNING_COPY_STYLE_CLASS = "warning-copy";
    protected static final String SUCCESS_COPY_STYLE_CLASS = "success-copy";
    protected static final String HELP_TEXT_STYLE_CLASS = "help-text";
    private static final String DANGER_COPY_STYLE_CLASS = "danger-copy";

    @FXML
    protected Label fintsServerStatusLabel;
    @FXML
    protected TextField bankNameField;
    @FXML
    protected TextField bankCodeField;
    @FXML
    protected TextField bicField;
    @FXML
    protected TextField fintsServerUrlField;
    @FXML
    protected TextField fintsUserIdField;
    @FXML
    protected TextField fintsCustomerIdField;
    @FXML
    protected PasswordField passwordField;
    @FXML
    protected TextField bankCommentField;
    @FXML
    protected Button saveButton;
    @FXML
    protected Button testConnectionButton;
    @FXML
    protected Button backButton;
    @FXML
    protected ScrollPane connectionPage;
    @FXML
    protected VBox accountSelectionPage;
    @FXML
    protected VBox accountSelectionHost;
    @FXML
    protected Label accountImportStatusLabel;

    protected final AccountService accountService;
    protected final BankingProtocol bankingProtocol;
    protected final BankService bankService;
    private final ActivityLogger activityLogger;
    protected BankAccountSelectionPane accountSelectionPane;
    protected AccountSetupWizard wizard;

    protected BankAccountDialogBase(AccountService accountService, BankingProtocol bankingProtocol,
                                    BankService bankService, ActivityLogger activityLogger) {
        this.accountService = accountService;
        this.bankingProtocol = bankingProtocol;
        this.bankService = bankService;
        this.activityLogger = activityLogger;
    }

    protected final void initializeAccountWizard(AccountSetupWizard.PageChangeListener pageChanged) {
        accountSelectionPane = new BankAccountSelectionPane();
        accountSelectionHost.getChildren().setAll(accountSelectionPane);
        VBox.setVgrow(accountSelectionPane, javafx.scene.layout.Priority.ALWAYS);
        wizard = new AccountSetupWizard(connectionPage, accountSelectionPage, backButton, saveButton,
                testConnectionButton, accountSelectionPane, pageChanged);
        accountSelectionPane.selectedCountProperty().addListener(
                (observable, oldValue, newValue) -> wizard.updatePrimaryButton());
    }

    @FXML
    protected void onPrimaryAction() {
        if (wizard.isShowingSelection()) {
            importSelectedAccounts();
        } else {
            onTestConnection();
        }
    }

    @FXML
    protected void onBack() {
        wizard.showConnectionPage();
    }

    @FXML
    protected void onCancel() {
        close();
    }

    protected abstract void onTestConnection();

    protected abstract void importSelectedAccounts();

    protected final String pinForConnection(String bankName) {
        String pin = passwordField.getText();
        if (pin == null || pin.isBlank()) {
            pin = PinPromptDialog.show(passwordField.getScene().getWindow(), bankName, 1, I18n.get("button.connect"));
            if (pin != null && !pin.isBlank()) {
                passwordField.setText(pin);
            }
        }
        return pin == null || pin.isBlank() ? null : pin;
    }

    protected final String bankName(Bank bank) {
        return bank == null || bank.getName() == null || bank.getName().isBlank()
                ? I18n.get("label.bankConnection") : bank.getName();
    }

    protected final void startConnectionTest(BankAccount testAccount, Bank testBank, String pin,
                                             Long activityAccountId, String bankName) {
        List<String> diagnostics = Collections.synchronizedList(new ArrayList<>());
        ConnectionTestContext context = new ConnectionTestContext(activityAccountId, bankName, diagnostics);
        recordConnectionDiagnostic(diagnostics, "Testing bank access for " + bankName);
        recordConnectionDiagnostic(diagnostics, "Fintrax-Version " + I18n.get("app.version"));
        log.info("Fintrax-Version {}: bank connection test started for {}", I18n.get("app.version"), bankName);

        testConnectionButton.setDisable(true);
        saveButton.setDisable(true);
        showEndpointStatus(I18n.get("message.fints.connectionTestInProgress"), HELP_TEXT_STYLE_CLASS);
        CompletableFuture.supplyAsync(() -> BankConnectionTestRunner.testAndDiscover(
                        bankingProtocol, testAccount, testBank, pin,
                        message -> recordConnectionDiagnostic(context.diagnostics(), message)))
                .whenComplete((result, failure) -> Platform.runLater(
                        () -> finishConnectionTest(context, result, failure)));
    }

    private void finishConnectionTest(ConnectionTestContext context, BankConnectionTestRunner.Result result,
                                      Throwable failure) {
        testConnectionButton.setDisable(false);
        saveButton.setDisable(false);
        if (failure != null) {
            finishConnectionFailure(context, failure);
        } else if (!result.connected()) {
            finishRejectedConnection(context);
        } else {
            finishConnectedBank(context, result.accounts());
        }
    }

    private void finishConnectionFailure(ConnectionTestContext context, Throwable failure) {
        String reason = connectionFailureReason(failure);
        recordConnectionDiagnostic(context.diagnostics(), "Bank access test failed: " + reason);
        log.warn("Bank connection test failed for {}: {}", context.bankName(), reason);
        boolean discoveryFailed = BankingException.FailureReason.ACCOUNT_DISCOVERY_FAILED.name().equals(reason);
        String messageKey = discoveryFailed
                ? "message.bankAccounts.discoveryFailed" : "message.fints.connectionTestFailed";
        showEndpointStatus(I18n.get(messageKey), WARNING_COPY_STYLE_CLASS);
        recordConnectionOutcome(context, I18n.get(messageKey));
    }

    private void finishRejectedConnection(ConnectionTestContext context) {
        recordConnectionDiagnostic(context.diagnostics(), "Bank rejected the supplied password / PIN.");
        log.warn("Bank connection test rejected credentials for {}", context.bankName());
        String message = I18n.get("message.fints.connectionTestRejected");
        showEndpointStatus(message, WARNING_COPY_STYLE_CLASS);
        recordConnectionOutcome(context, message);
    }

    private void finishConnectedBank(ConnectionTestContext context, List<DiscoveredBankAccount> discovered) {
        recordConnectionDiagnostic(context.diagnostics(), "Bank access successfully tested.");
        log.info("Bank connection test succeeded for {}", context.bankName());
        boolean noAccountsFound = discovered.isEmpty();
        if (noAccountsFound) {
            showEndpointStatus(I18n.get("message.bankAccounts.noneFound"), WARNING_COPY_STYLE_CLASS);
        } else {
            showEndpointStatus(I18n.get("message.fints.connectionTestSuccess"), SUCCESS_COPY_STYLE_CLASS);
        }
        recordConnectionOutcome(context, I18n.get("message.fints.connectionTestSuccess"));
        if (!noAccountsFound) {
            accountSelectionPane.setAccounts(discovered,
                    BankAccountSelectionPane.existingIbans(accountService.getAllAccounts()));
            accountImportStatusLabel.setText("");
            wizard.showSelectionPage();
        }
    }

    private void recordConnectionDiagnostic(List<String> diagnostics, String message) {
        diagnostics.add(LocalDateTime.now(ZoneId.systemDefault()).format(DIAGNOSTIC_TIME) + " " + message);
    }

    private void recordConnectionOutcome(ConnectionTestContext context, String summary) {
        activityLogger.log(ActivityAction.EXECUTE, EntityType.ACCOUNT, context.accountId(),
                summary + " — " + context.bankName() + ACTIVITY_DIAGNOSTICS_MARKER
                        + String.join("\n", context.diagnostics()));
    }

    private String connectionFailureReason(Throwable failure) {
        Throwable cause = failure;
        while (cause != null && !(cause instanceof BankingException)) {
            cause = cause.getCause();
        }
        return cause instanceof BankingException bankingException
                ? bankingException.getFailureReason().name() : failure.getClass().getSimpleName();
    }

    protected final void showEndpointStatus(String message, String styleClass) {
        fintsServerStatusLabel.setText(message);
        fintsServerStatusLabel.getStyleClass().removeAll(HELP_TEXT_STYLE_CLASS,
                SUCCESS_COPY_STYLE_CLASS, WARNING_COPY_STYLE_CLASS, DANGER_COPY_STYLE_CLASS);
        fintsServerStatusLabel.getStyleClass().add(styleClass);
    }

    protected final void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        FintraxUI.configureDialog(alert);
        alert.showAndWait();
    }

    protected final void close() {
        passwordField.clear();
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }

    private record ConnectionTestContext(Long accountId, String bankName, List<String> diagnostics) {
    }
}
