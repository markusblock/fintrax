package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankDirectoryEntry;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.DiscoveredBankAccount;
import org.fintrax.fintx.PinStorage;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
@Scope("prototype")
public class EditAccountDialogController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DIAGNOSTIC_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final String ACTIVITY_DIAGNOSTICS_MARKER = "\n[FINTRAX_SYNC_DIAGNOSTICS]\n";

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
    private CheckBox storePasswordCheckBox;
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

    private final AccountService accountService;
    private final BankService bankService;
    private final BankingProtocol bankingProtocol;
    private final PinStorage pinStorage;
    private final ActivityLogger activityLogger;
    private BankAccount account;
    private Bank bank;

    public EditAccountDialogController(AccountService accountService, BankService bankService,
                                       BankingProtocol bankingProtocol, PinStorage pinStorage,
                                       ActivityLogger activityLogger) {
        this.accountService = accountService;
        this.bankService = bankService;
        this.bankingProtocol = bankingProtocol;
        this.pinStorage = pinStorage;
        this.activityLogger = activityLogger;
    }

    @FXML
    public void initialize() {
        accountTypeCombo.getItems().setAll(AccountType.values());
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
                showEndpointStatus(I18n.get(messageKey), "warning-copy");
                return;
            }
            boolean hasManualUrl = !fintsServerUrlField.getText().isBlank();
            if (!hasManualUrl) {
                fintsServerUrlField.setText(url);
            }
            String messageKey = hasManualUrl
                    ? "message.fints.lookupFoundManual" : "message.fints.lookupFound";
            showEndpointStatus(I18n.get(messageKey), "success-copy");
        } catch (BankingException e) {
            String messageKey = switch (e.getFailureReason()) {
                case INVALID_IBAN -> "message.sync.invalidIban";
                case INVALID_ENDPOINT -> "message.sync.endpointInvalid";
                default -> "message.fints.lookupFailed";
            };
            showEndpointStatus(I18n.get(messageKey), "warning-copy");
        }
    }

    @FXML
    private void onTestConnection() {
        String pin = passwordField.getText();
        if (pin == null || pin.isBlank()) {
            pin = pinStorage.retrievePin(String.valueOf(account.getId()));
        }
        if (pin == null || pin.isBlank()) {
            pin = promptForPin();
            if (pin == null || pin.isBlank()) {
                return;
            }
            passwordField.setText(pin);
        }

        Bank testBank = bankProfileFromFields();
        String bankName = testBank.getName() == null || testBank.getName().isBlank()
                ? I18n.get("label.bankConnection") : testBank.getName();
        List<String> diagnostics = Collections.synchronizedList(new ArrayList<>());
        recordConnectionDiagnostic(diagnostics, "Testing bank access for " + bankName);
        recordConnectionDiagnostic(diagnostics, "Fintrax-Version " + I18n.get("app.version"));
        log.info("Fintrax-Version {}: bank connection test started for {}", I18n.get("app.version"), bankName);

        testConnectionButton.setDisable(true);
        saveButton.setDisable(true);
        showEndpointStatus(I18n.get("message.fints.connectionTestInProgress"), "help-text");
        String testPin = pin;
        CompletableFuture.supplyAsync(() -> {
            try {
                boolean valid = bankingProtocol.validatePin(account, testBank, testPin,
                        message -> recordConnectionDiagnostic(diagnostics, message));
                if (!valid) {
                    return new AccountDiscoveryResult(false, List.of());
                }
                recordConnectionDiagnostic(diagnostics, "Starting bank account discovery");
                List<DiscoveredBankAccount> discovered = bankingProtocol.discoverAccounts(account, testBank, testPin,
                        message -> recordConnectionDiagnostic(diagnostics, message));
                return new AccountDiscoveryResult(true, discovered);
            } catch (BankingException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((valid, failure) -> Platform.runLater(() -> {
            testConnectionButton.setDisable(false);
            saveButton.setDisable(false);
            String summary;
            if (failure != null) {
                String reason = connectionFailureReason(failure);
                recordConnectionDiagnostic(diagnostics, "Bank access test failed: " + reason);
                log.warn("Bank connection test failed for {}: {}", bankName, reason);
                boolean discoveryFailed = BankingException.FailureReason.ACCOUNT_DISCOVERY_FAILED.name().equals(reason);
                String messageKey = discoveryFailed
                        ? "message.bankAccounts.discoveryFailed" : "message.fints.connectionTestFailed";
                showEndpointStatus(I18n.get(messageKey), "warning-copy");
                summary = I18n.get(messageKey);
            } else if (valid.connected()) {
                recordConnectionDiagnostic(diagnostics, "Bank access successfully tested.");
                log.info("Bank connection test succeeded for {}", bankName);
                showEndpointStatus(valid.accounts().isEmpty()
                        ? I18n.get("message.bankAccounts.noneFound")
                        : I18n.get("message.fints.connectionTestSuccess"),
                        valid.accounts().isEmpty() ? "warning-copy" : "success-copy");
                summary = I18n.get("message.fints.connectionTestSuccess");
            } else {
                recordConnectionDiagnostic(diagnostics, "Bank rejected the supplied password / PIN.");
                log.warn("Bank connection test rejected credentials for {}", bankName);
                showEndpointStatus(I18n.get("message.fints.connectionTestRejected"), "warning-copy");
                summary = I18n.get("message.fints.connectionTestRejected");
            }
            activityLogger.log(ActivityAction.EXECUTE, EntityType.ACCOUNT, account.getId(),
                    summary + " — " + bankName + ACTIVITY_DIAGNOSTICS_MARKER + String.join("\n", diagnostics));
            if (failure == null && valid.connected() && !valid.accounts().isEmpty()) {
                List<DiscoveredBankAccount> selected = BankAccountSelectionDialog.show(
                        testConnectionButton.getScene().getWindow(), valid.accounts(), existingIbans());
                if (!selected.isEmpty()) {
                    importDiscoveredAccounts(selected, testPin);
                }
            }
        }));
    }

    private Set<String> existingIbans() {
        Set<String> ibans = new LinkedHashSet<>();
        accountService.getAllAccounts().stream().map(BankAccount::getIban)
                .map(BankAccountSelectionDialog::normalizeIban).filter(iban -> !iban.isBlank()).forEach(ibans::add);
        return ibans;
    }

    private void importDiscoveredAccounts(List<DiscoveredBankAccount> discovered, String pin) {
        try {
            String bic = bicField.getText().trim();
            bank = bankService.updateBank(bank.getId(), bankNameField.getText().trim(), bic.isBlank() ? null : bic,
                    fintsServerUrlField.getText().trim(), fintsUserIdField.getText().trim(),
                    fintsCustomerIdField.getText().trim(), bankCommentField.getText().trim());
            accountService.updateAccount(account.getId(), bank.getId(), accountNameField.getText().trim(),
                    accountHolderField.getText().trim(), accountTypeCombo.getValue(), currencyField.getText().trim(),
                    accountCommentField.getText().trim());
            if (storePasswordCheckBox.isSelected()) {
                pinStorage.storePin(String.valueOf(account.getId()), pin);
            }

            int imported = 0;
            for (DiscoveredBankAccount discoveredAccount : discovered) {
                String iban = BankAccountSelectionDialog.normalizeIban(discoveredAccount.iban());
                if (iban.isBlank()) continue;
                BankAccount created = accountService.createAccount(bank.getId(), iban,
                        BankAccountSelectionDialog.suggestedAccountName(discoveredAccount),
                        discoveredAccount.accountHolder(), accountTypeFor(discoveredAccount),
                        defaultCurrency(discoveredAccount.currency()), null);
                if (storePasswordCheckBox.isSelected()) {
                    pinStorage.storePin(String.valueOf(created.getId()), pin);
                }
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

    private AccountType accountTypeFor(DiscoveredBankAccount discovered) {
        String bankType = (discovered.bankType() == null ? "" : discovered.bankType()).toLowerCase(Locale.ROOT);
        if (bankType.contains("spar") || bankType.contains("saving") || bankType.contains("tagesgeld")) {
            return AccountType.SAVINGS;
        }
        if (bankType.contains("kredit") || bankType.contains("credit") || bankType.contains("karte")) {
            return AccountType.CREDIT_CARD;
        }
        return AccountType.GIRO;
    }

    private String defaultCurrency(String currency) {
        return currency == null || currency.isBlank() ? "EUR" : currency.trim();
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onSave() {
        String password = passwordField.getText();
        if (storePasswordCheckBox.isSelected() && (password == null || password.isBlank())) {
            password = pinStorage.retrievePin(String.valueOf(account.getId()));
            if (password == null || password.isBlank()) {
                password = promptForPin();
                if (password == null || password.isBlank()) {
                    return;
                }
                passwordField.setText(password);
            }
        }

        String bic = bicField.getText().trim();
        bank = bankService.updateBank(bank.getId(), bankNameField.getText().trim(), bic.isBlank() ? null : bic,
                fintsServerUrlField.getText().trim(), fintsUserIdField.getText().trim(),
                fintsCustomerIdField.getText().trim(), bankCommentField.getText().trim());
        accountService.updateAccount(account.getId(), bank.getId(), accountNameField.getText().trim(),
                accountHolderField.getText().trim(), accountTypeCombo.getValue(), currencyField.getText().trim(),
                accountCommentField.getText().trim());
        if (storePasswordCheckBox.isSelected()) {
            pinStorage.storePin(String.valueOf(account.getId()), password);
        }
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
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(I18n.get("dialog.pin.title"));
        String bankName = bankNameField.getText().isBlank()
                ? I18n.get("label.bankConnection") : bankNameField.getText().trim();
        dialog.setHeaderText(I18n.get("dialog.pin.header", bankName));
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(passwordField.getScene().getWindow());

        PasswordField pinField = new PasswordField();
        pinField.setPromptText(I18n.get("dialog.pin.prompt"));
        VBox content = new VBox(8, new Label(I18n.get("dialog.pin.prompt")), pinField);
        content.setPadding(new Insets(10, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        ButtonType testButton = new ButtonType(I18n.get("button.testConnection"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(testButton, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == testButton ? pinField.getText() : null);
        dialog.setOnHidden(event -> pinField.clear());
        FintraxUI.configureDialog(dialog);
        return dialog.showAndWait().orElse(null);
    }

    private void recordConnectionDiagnostic(List<String> diagnostics, String message) {
        diagnostics.add(LocalDateTime.now().format(DIAGNOSTIC_TIME) + " " + message);
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

    private record AccountDiscoveryResult(boolean connected, List<DiscoveredBankAccount> accounts) {
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
        fintsServerStatusLabel.getStyleClass().removeAll("help-text", "success-copy", "warning-copy", "danger-copy");
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
