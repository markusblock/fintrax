package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankDirectoryEntry;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.DiscoveredBankAccount;
import org.fintrax.model.ActivityAction;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;
import org.fintrax.model.AccountType;
import org.fintrax.model.EntityType;
import org.fintrax.service.ActivityLogger;
import org.fintrax.service.AccountService;
import org.fintrax.service.BankService;
import org.fintrax.ui.FintraxUI;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@Scope("prototype")
public class EditAccountDialogController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DIAGNOSTIC_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final String ACTIVITY_DIAGNOSTICS_MARKER = "\n[FINTRAX_SYNC_DIAGNOSTICS]\n";
    private static final String HELP_TEXT_STYLE_CLASS = "help-text";
    private static final String WARNING_COPY_STYLE_CLASS = "warning-copy";
    private static final String SUCCESS_COPY_STYLE_CLASS = "success-copy";
    private static final String INVALID_IBAN_MESSAGE_KEY = "message.sync.invalidIban";

    @FXML
    private TextField bankNameField;
    @FXML
    private TextField bankCodeField;
    @FXML
    private TextField bicField;
    @FXML
    private TextField fintsServerUrlField;
    @FXML
    private Label fintsServerStatusLabel;
    @FXML
    private TextField fintsUserIdField;
    @FXML
    private TextField fintsCustomerIdField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField bankCommentField;
    @FXML
    private TextField accountNameField;
    @FXML
    private TextField accountHolderField;
    @FXML
    private ComboBox<AccountType> accountTypeCombo;
    @FXML
    private TextField ibanField;
    @FXML
    private TextField currencyField;
    @FXML
    private TextArea accountCommentField;
    @FXML
    private Label balanceValue;
    @FXML
    private Label balanceDateValue;
    @FXML
    private Label lastSyncValue;
    @FXML
    private Button saveButton;
    @FXML
    private Button testConnectionButton;
    @FXML
    private Button backButton;
    @FXML
    private ScrollPane connectionPage;
    @FXML
    private VBox accountSelectionPage;
    @FXML
    private VBox accountSelectionHost;
    @FXML
    private Label accountImportStatusLabel;

    private final AccountService accountService;
    private final BankService bankService;
    private final BankingProtocol bankingProtocol;
    private final ActivityLogger activityLogger;
    private BankAccount account;
    private Bank bank;
    private BankAccountSelectionPane accountSelectionPane;
    private AccountSetupWizard wizard;

    public EditAccountDialogController(AccountService accountService, BankService bankService,
                                       BankingProtocol bankingProtocol, ActivityLogger activityLogger) {
        this.accountService = accountService;
        this.bankService = bankService;
        this.bankingProtocol = bankingProtocol;
        this.activityLogger = activityLogger;
    }

    @FXML
    public void initialize() {
        accountTypeCombo.getItems().setAll(AccountType.values());
        accountSelectionPane = new BankAccountSelectionPane();
        accountSelectionHost.getChildren().setAll(accountSelectionPane);
        VBox.setVgrow(accountSelectionPane, Priority.ALWAYS);
        wizard = new AccountSetupWizard(connectionPage, accountSelectionPage, backButton, saveButton,
                testConnectionButton, accountSelectionPane, null, null);
        accountSelectionPane.selectedCountProperty().addListener(
                (observable, oldValue, newValue) -> wizard.updatePrimaryButton());
    }

    public void setAccount(BankAccount account) {
        this.account = account;
        this.bank = bankService.getBankForAccount(account)
                .orElseGet(() -> bankService.findOrCreateLegacy(account.getIban(), account.getBic(),
                        account.getFintsServerUrl(), account.getBankName()));
        bankNameField.setText(valueOrEmpty(bank.getName()));
        bankCodeField.setText(valueOrEmpty(bank.getBlz()));
        bicField.setText(valueOrEmpty(bank.getBic()));
        fintsServerUrlField.setText(valueOrEmpty(bank.getFintsServerUrl()));
        fintsUserIdField.setText(valueOrEmpty(bank.getFintsUserId()));
        fintsCustomerIdField.setText(valueOrEmpty(bank.getFintsCustomerId()));
        bankCommentField.setText(valueOrEmpty(bank.getComment()));
        accountNameField.setText(valueOrEmpty(account.getAccountName() == null
                ? account.getBankName() : account.getAccountName()));
        accountHolderField.setText(valueOrEmpty(account.getAccountHolder()));
        accountTypeCombo.setValue(account.getAccountType());
        ibanField.setText(valueOrEmpty(account.getIban()));
        currencyField.setText(valueOrEmpty(account.getCurrency()));
        accountCommentField.setText(valueOrEmpty(account.getComment()));
        balanceValue.setText(formatBalance(account));
        balanceDateValue.setText(account.getBalanceDate() == null
                ? "—" : account.getBalanceDate().format(DATE_TIME_FORMAT));
        lastSyncValue.setText(account.getLastSyncAt() == null
                ? I18n.get("label.neverSynced") : account.getLastSyncAt().format(DATE_TIME_FORMAT));
    }

    @FXML
    private void onFindFinTsServer() {
        try {
            BankDirectoryEntry entry = bankingProtocol.lookupBankDetails(account.getIban());
            String url = entry == null ? null : entry.pinTanAddress();
            if (url == null || url.isBlank()) {
                String messageKey = fintsServerUrlField.getText().isBlank()
                        ? "message.fints.lookupNotFound" : "message.fints.lookupNotFoundManual";
                showEndpointStatus(I18n.get(messageKey), WARNING_COPY_STYLE_CLASS);
                return;
            }
            boolean hasManualUrl = !fintsServerUrlField.getText().isBlank();
            if (!hasManualUrl) {
                fintsServerUrlField.setText(url);
            }
            String messageKey = hasManualUrl
                    ? "message.fints.lookupFoundManual" : "message.fints.lookupFound";
            showEndpointStatus(I18n.get(messageKey), SUCCESS_COPY_STYLE_CLASS);
        } catch (BankingException e) {
            String messageKey = switch (e.getFailureReason()) {
                case INVALID_IBAN -> INVALID_IBAN_MESSAGE_KEY;
                case INVALID_ENDPOINT -> "message.sync.endpointInvalid";
                default -> "message.fints.lookupFailed";
            };
            showEndpointStatus(I18n.get(messageKey), WARNING_COPY_STYLE_CLASS);
        }
    }

    @FXML
    private void onPrimaryAction() {
        if (wizard.isShowingSelection()) {
            importSelectedAccounts();
        } else {
            onTestConnection();
        }
    }

    @FXML
    private void onBack() {
        wizard.showConnectionPage();
    }

    private void onTestConnection() {
        String pin = pinForConnection();
        if (pin == null) return;
        Bank testBank = bankProfileFromFields();
        List<String> diagnostics = Collections.synchronizedList(new ArrayList<>());
        String bankName = bankName(testBank);
        ConnectionTestContext context = new ConnectionTestContext(testBank, pin, bankName, diagnostics);
        recordConnectionDiagnostic(diagnostics, "Testing bank access for " + bankName);
        recordConnectionDiagnostic(diagnostics, "Fintrax-Version " + I18n.get("app.version"));
        log.info("Fintrax-Version {}: bank connection test started for {}", I18n.get("app.version"), bankName);

        testConnectionButton.setDisable(true);
        saveButton.setDisable(true);
        showEndpointStatus(I18n.get("message.fints.connectionTestInProgress"), HELP_TEXT_STYLE_CLASS);
        CompletableFuture.supplyAsync(() -> BankConnectionTestRunner.testAndDiscover(
                        bankingProtocol, account, context.bank(), context.pin(),
                        message -> recordConnectionDiagnostic(context.diagnostics(), message)))
                .whenComplete((result, failure) -> Platform.runLater(
                        () -> finishConnectionTest(context, result, failure)));
    }

    private String pinForConnection() {
        String pin = passwordField.getText();
        if (pin == null || pin.isBlank()) {
            pin = promptForPin();
            if (pin != null && !pin.isBlank()) {
                passwordField.setText(pin);
            }
        }
        return pin == null || pin.isBlank() ? null : pin;
    }

    private String bankName(Bank testBank) {
        return testBank.getName() == null || testBank.getName().isBlank()
                ? I18n.get("label.bankConnection") : testBank.getName();
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
        String messageKey = noAccountsFound
                ? "message.bankAccounts.noneFound" : "message.fints.connectionTestSuccess";
        showEndpointStatus(I18n.get(messageKey), noAccountsFound ? WARNING_COPY_STYLE_CLASS : SUCCESS_COPY_STYLE_CLASS);
        recordConnectionOutcome(context, I18n.get("message.fints.connectionTestSuccess"));
        if (!noAccountsFound) {
            accountSelectionPane.setAccounts(discovered,
                    BankAccountSelectionPane.existingIbans(accountService.getAllAccounts()));
            accountImportStatusLabel.setText("");
            wizard.showSelectionPage();
        }
    }

    private void recordConnectionOutcome(ConnectionTestContext context, String summary) {
        activityLogger.log(ActivityAction.EXECUTE, EntityType.ACCOUNT, account.getId(),
                summary + " — " + context.bankName() + ACTIVITY_DIAGNOSTICS_MARKER
                        + String.join("\n", context.diagnostics()));
    }

    private void importSelectedAccounts() {
        List<DiscoveredBankAccount> selected = accountSelectionPane.selectedAccounts();
        if (!selected.isEmpty()) {
            importDiscoveredAccounts(selected);
        }
    }

    private void importDiscoveredAccounts(List<DiscoveredBankAccount> discovered) {
        try {
            String bic = bicField.getText().trim();
            bank = bankService.updateBank(bank.getId(), bankNameField.getText().trim(), bic.isBlank() ? null : bic,
                    fintsServerUrlField.getText().trim(), fintsUserIdField.getText().trim(),
                    fintsCustomerIdField.getText().trim(), bankCommentField.getText().trim());
            accountService.updateAccount(account.getId(), bank.getId(), accountNameField.getText().trim(),
                    accountHolderField.getText().trim(), accountTypeCombo.getValue(), currencyField.getText().trim(),
                    accountCommentField.getText().trim());
            int imported = 0;
            for (DiscoveredBankAccount discoveredAccount : discovered) {
                String iban = BankAccountSelectionPane.normalizeIban(discoveredAccount.iban());
                if (iban.isBlank()) continue;
                accountService.createAccount(bank.getId(), iban,
                        BankAccountSelectionPane.suggestedAccountName(discoveredAccount),
                        discoveredAccount.accountHolder(), BankAccountSelectionPane.accountTypeFor(discoveredAccount),
                        BankAccountSelectionPane.defaultCurrency(discoveredAccount.currency()), null);
                imported++;
            }
            if (imported > 0) {
                log.info("Imported {} bank account(s) from {}", imported, bank.getName());
                close();
            }
        } catch (RuntimeException e) {
            log.warn("Bank account import failed for account {}", account.getId());
            showError(I18n.get("message.bankAccounts.importFailed"));
        }
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onSave() {
        String bic = bicField.getText().trim();
        bank = bankService.updateBank(bank.getId(), bankNameField.getText().trim(), bic.isBlank() ? null : bic,
                fintsServerUrlField.getText().trim(), fintsUserIdField.getText().trim(),
                fintsCustomerIdField.getText().trim(), bankCommentField.getText().trim());
        accountService.updateAccount(account.getId(), bank.getId(), accountNameField.getText().trim(),
                accountHolderField.getText().trim(), accountTypeCombo.getValue(), currencyField.getText().trim(),
                accountCommentField.getText().trim());
        close();
    }

    private Bank bankProfileFromFields() {
        return Bank.builder()
                .id(bank.getId())
                .name(emptyToNull(bankNameField.getText()))
                .blz(emptyToNull(bankCodeField.getText()))
                .bic(emptyToNull(bicField.getText()))
                .fintsServerUrl(emptyToNull(fintsServerUrlField.getText()))
                .fintsUserId(emptyToNull(fintsUserIdField.getText()))
                .fintsCustomerId(emptyToNull(fintsCustomerIdField.getText()))
                .comment(emptyToNull(bankCommentField.getText()))
                .build();
    }

    private String promptForPin() {
        String bankName = bankNameField.getText().isBlank()
                ? I18n.get("label.bankConnection") : bankNameField.getText().trim();
        return PinPromptDialog.show(passwordField.getScene().getWindow(), bankName, 1, I18n.get("button.connect"));
    }

    private void recordConnectionDiagnostic(List<String> diagnostics, String message) {
        diagnostics.add(LocalDateTime.now(ZoneId.systemDefault()).format(DIAGNOSTIC_TIME) + " " + message);
    }

    private String connectionFailureReason(Throwable failure) {
        Throwable cause = failure;
        while (cause != null && !(cause instanceof BankingException)) {
            cause = cause.getCause();
        }
        return cause instanceof BankingException bankingException
                ? bankingException.getFailureReason().name() : failure.getClass().getSimpleName();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ConnectionTestContext(Bank bank, String pin, String bankName, List<String> diagnostics) {
    }

    private String formatBalance(BankAccount bankAccount) {
        if (bankAccount.getBalance() == null) return "—";
        return String.format("%,.2f %s", bankAccount.getBalance().doubleValue(),
                valueOrEmpty(bankAccount.getCurrency()));
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void showEndpointStatus(String message, String styleClass) {
        fintsServerStatusLabel.setText(message);
        fintsServerStatusLabel.getStyleClass().removeAll(HELP_TEXT_STYLE_CLASS,
                SUCCESS_COPY_STYLE_CLASS, WARNING_COPY_STYLE_CLASS, "danger-copy");
        fintsServerStatusLabel.getStyleClass().add(styleClass);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        FintraxUI.configureDialog(alert);
        alert.showAndWait();
    }

    private void close() {
        passwordField.clear();
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
}
