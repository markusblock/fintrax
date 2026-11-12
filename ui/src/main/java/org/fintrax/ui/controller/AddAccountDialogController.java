package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
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
import org.fintrax.model.AccountType;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;
import org.fintrax.model.EntityType;
import org.fintrax.service.ActivityLogger;
import org.fintrax.service.AccountService;
import org.fintrax.service.BankService;
import org.fintrax.ui.FintraxUI;
import org.springframework.beans.factory.annotation.Autowired;
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
public class AddAccountDialogController {
    private static final String ACTIVITY_DIAGNOSTICS_MARKER = "\n[FINTRAX_SYNC_DIAGNOSTICS]\n";
    private static final DateTimeFormatter DIAGNOSTIC_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    @FXML
    private TextField ibanField;
    @FXML
    private TextField bicField;
    @FXML
    private TextField bankCodeField;
    @FXML
    private TextField bankNameField;
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
    private TextField currencyField;
    @FXML
    private TextField accountCommentField;
    @FXML
    private Button saveButton;
    @FXML
    private Button testConnectionButton;

    private final AccountService accountService;
    private final BankingProtocol bankingProtocol;
    private final BankService bankService;
    private final PinStorage pinStorage;
    private final ActivityLogger activityLogger;
    private Bank matchedBank;
    private String checkedIban;

    @Autowired
    public AddAccountDialogController(AccountService accountService, BankingProtocol bankingProtocol,
                                      BankService bankService, PinStorage pinStorage, ActivityLogger activityLogger) {
        this.accountService = accountService;
        this.bankingProtocol = bankingProtocol;
        this.bankService = bankService;
        this.pinStorage = pinStorage;
        this.activityLogger = activityLogger;
    }

    @FXML
    public void initialize() {
        accountTypeCombo.getItems().setAll(AccountType.values());
        accountTypeCombo.setValue(AccountType.GIRO);
        currencyField.setText("EUR");
        bankCodeField.setEditable(false);
        ibanField.textProperty().addListener((observable, oldValue, newValue) -> refreshBankProfileForIban());
    }

    @FXML
    private void onFindFinTsServer() {
        String iban = normalizeIban(ibanField.getText());
        try {
            BankDirectoryEntry entry = bankService.lookupBankDirectoryEntry(iban);
            String url = entry == null ? null : emptyToNull(entry.pinTanAddress());
            if (url == null || url.isBlank()) {
                String messageKey = fintsServerUrlField.getText().isBlank()
                        ? "message.fints.lookupNotFound" : "message.fints.lookupNotFoundManual";
                showEndpointStatus(I18n.get(messageKey), "warning-copy");
                return;
            }
            boolean hasManualUrl = !fintsServerUrlField.getText().isBlank();
            if (matchedBank == null && !hasManualUrl) {
                fintsServerUrlField.setText(url);
                applyDirectoryEntry(entry);
            }
            String messageKey = matchedBank != null || !hasManualUrl
                    ? "message.fints.lookupFound" : "message.fints.lookupFoundManual";
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
        String password = passwordField.getText();
        if (password == null || password.isBlank()) {
            password = promptForPin();
            if (password == null || password.isBlank()) {
                return;
            }
            passwordField.setText(password);
        }

        String iban = normalizeIban(ibanField.getText());
        BankAccount account = BankAccount.builder().iban(iban).build();
        Bank bank = currentBankProfile();
        String pin = password;
        List<String> diagnostics = Collections.synchronizedList(new ArrayList<>());
        String bankName = bank.getName() == null || bank.getName().isBlank()
                ? I18n.get("label.bankConnection") : bank.getName();
        recordConnectionDiagnostic(diagnostics, "Testing bank access for " + bankName);
        recordConnectionDiagnostic(diagnostics, "Fintrax-Version " + I18n.get("app.version"));
        log.info("Fintrax-Version {}: bank connection test started for {}", I18n.get("app.version"), bankName);

        testConnectionButton.setDisable(true);
        saveButton.setDisable(true);
        showEndpointStatus(I18n.get("message.fints.connectionTestInProgress"), "help-text");
        CompletableFuture.supplyAsync(() -> {
            try {
                boolean valid = bankingProtocol.validatePin(account, bank, pin,
                        message -> recordConnectionDiagnostic(diagnostics, message));
                if (!valid) {
                    return new AccountDiscoveryResult(false, List.of());
                }
                recordConnectionDiagnostic(diagnostics, "Starting bank account discovery");
                List<DiscoveredBankAccount> discovered = bankingProtocol.discoverAccounts(account, bank, pin,
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
                summary = I18n.get(messageKey) + " — " + bankName;
            } else if (valid.connected()) {
                recordConnectionDiagnostic(diagnostics, "Bank access successfully tested.");
                log.info("Bank connection test succeeded for {}", bankName);
                if (valid.accounts().isEmpty()) {
                    showEndpointStatus(I18n.get("message.bankAccounts.noneFound"), "warning-copy");
                    summary = I18n.get("message.fints.connectionTestSuccess") + " — " + bankName;
                } else {
                    showEndpointStatus(I18n.get("message.fints.connectionTestSuccess"), "success-copy");
                    summary = I18n.get("message.fints.connectionTestSuccess") + " — " + bankName;
                }
            } else {
                recordConnectionDiagnostic(diagnostics, "Bank rejected the supplied password / PIN.");
                log.warn("Bank connection test rejected credentials for {}", bankName);
                showEndpointStatus(I18n.get("message.fints.connectionTestRejected"), "warning-copy");
                summary = I18n.get("message.fints.connectionTestRejected") + " — " + bankName;
            }
            String description = summary + ACTIVITY_DIAGNOSTICS_MARKER + String.join("\n", diagnostics);
            activityLogger.log(ActivityAction.EXECUTE, EntityType.ACCOUNT, null, description);
            if (failure == null && valid.connected() && !valid.accounts().isEmpty()) {
                List<DiscoveredBankAccount> selected = BankAccountSelectionDialog.show(
                        testConnectionButton.getScene().getWindow(), valid.accounts(), existingIbans());
                if (!selected.isEmpty()) {
                    importDiscoveredAccounts(selected, pin, iban, bicField.getText());
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

    private void importDiscoveredAccounts(List<DiscoveredBankAccount> discovered, String pin,
                                          String sourceIban, String sourceBic) {
        try {
            Bank bank = ensureBankProfile(sourceIban, sourceBic);
            if (bank == null) return;
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
        } catch (BankingException | RuntimeException e) {
            log.warn("Bank account import failed");
            showError(I18n.get("message.bankAccounts.importFailed"));
        }
    }

    private Bank ensureBankProfile(String iban, String bic) throws BankingException {
        Bank existing = bankService.findForIban(iban, bic).orElse(matchedBank);
        if (existing != null) return existing;

        BankDirectoryEntry entry = bankService.lookupBankDirectoryEntry(iban);
        String bankCode = entry == null ? bankService.bankCodeForGermanIban(iban) : entry.blz();
        if (bankCode == null) {
            showError(I18n.get("message.sync.invalidIban"));
            return null;
        }
        String name = emptyToNull(bankNameField.getText());
        if (name == null && entry != null) name = emptyToNull(entry.name());
        String userId = emptyToNull(fintsUserIdField.getText());
        if (name == null || userId == null) {
            showError(I18n.get(name == null ? "validation.bankName.required" : "validation.fintsUserId.required"));
            return null;
        }
        String bankBic = emptyToNull(bicField.getText());
        if (bankBic == null && entry != null) bankBic = emptyToNull(entry.bic());
        String endpoint = emptyToNull(fintsServerUrlField.getText());
        if (endpoint == null && entry != null) endpoint = emptyToNull(entry.pinTanAddress());
        matchedBank = bankService.createBank(bankCode, name, bankBic, endpoint, userId,
                emptyToNull(fintsCustomerIdField.getText()), emptyToNull(bankCommentField.getText()));
        return matchedBank;
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

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onSave() {
        String iban = normalizeIban(ibanField.getText());
        String bic = emptyToNull(bicField.getText());
        String bankName = bankNameField.getText().trim();
        String fintsServerUrl = emptyToNull(fintsServerUrlField.getText());
        String fintsUserId = emptyToNull(fintsUserIdField.getText());
        String fintsCustomerId = emptyToNull(fintsCustomerIdField.getText());
        String bankComment = bankCommentField.getText().trim();
        String accountName = accountNameField.getText().trim();
        String accountHolder = accountHolderField.getText().trim();
        String accountComment = accountCommentField.getText().trim();
        AccountType accountType = accountTypeCombo.getValue();

        if (iban.isEmpty()) {
            showError(I18n.get("validation.iban.required"));
            return;
        }
        if (accountName.isBlank()) {
            showError(I18n.get("validation.accountName.required"));
            return;
        }

        String password = passwordField.getText();
        if (storePasswordCheckBox.isSelected() && (password == null || password.isBlank())) {
            password = promptForPin();
            if (password == null || password.isBlank()) {
                return;
            }
            passwordField.setText(password);
        }

        BankDirectoryEntry directoryEntry;
        try {
            directoryEntry = bankService.lookupBankDirectoryEntry(iban);
        } catch (BankingException e) {
            showError(e.getFailureReason() == BankingException.FailureReason.INVALID_IBAN
                    ? I18n.get("message.sync.invalidIban") : I18n.get("message.fints.lookupFailed"));
            return;
        }
        String bankCode = directoryEntry == null
                ? bankService.bankCodeForGermanIban(iban) : directoryEntry.blz();
        if (bankCode == null) {
            showError(I18n.get("message.sync.invalidIban"));
            return;
        }

        matchedBank = bankService.findForIban(iban, bic).orElse(matchedBank);
        Bank bank = matchedBank;
        if (bank == null) {
            if (bankName.isBlank()) {
                bankName = directoryEntry == null ? "" : emptyToNull(directoryEntry.name());
            }
            if (bic == null && directoryEntry != null) {
                bic = emptyToNull(directoryEntry.bic());
            }
            bank = Bank.builder()
                    .name(emptyToNull(bankName))
                    .blz(bankCode)
                    .bic(bic)
                    .fintsServerUrl(fintsServerUrl != null ? fintsServerUrl
                            : directoryEntry == null ? null : emptyToNull(directoryEntry.pinTanAddress()))
                    .fintsUserId(fintsUserId)
                    .fintsCustomerId(fintsCustomerId)
                    .comment(bankComment.isBlank() ? null : bankComment)
                    .build();
            if (bank.getName() == null) {
                showError(I18n.get("validation.bankName.required"));
                return;
            }
            if (bank.getFintsUserId() == null) {
                showError(I18n.get("validation.fintsUserId.required"));
                return;
            }
        }
        if (matchedBank == null) {
            bank = bankService.createBank(bankCode, bank.getName(), bank.getBic(), bank.getFintsServerUrl(),
                    bank.getFintsUserId(), bank.getFintsCustomerId(), bank.getComment());
        }
        BankAccount account = accountService.createAccount(bank.getId(), iban, accountName, accountHolder, accountType,
                emptyToNull(currencyField.getText()), accountComment);
        if (storePasswordCheckBox.isSelected()) {
            pinStorage.storePin(String.valueOf(account.getId()), password);
        }

        log.info("Added bank account");
        close();
    }

    private void refreshBankProfileForIban() {
        String iban = normalizeIban(ibanField.getText());
        if (!iban.matches("DE[0-9]{20}")) {
            if (checkedIban != null) {
                checkedIban = null;
                matchedBank = null;
                clearBankProfile();
                setBankProfileReadOnly(false);
            }
            return;
        }
        if (iban.equals(checkedIban)) {
            return;
        }
        checkedIban = iban;
        String typedBic = matchedBank == null ? bicField.getText() : "";
        bankCodeField.setText(bankService.bankCodeForGermanIban(iban));
        matchedBank = bankService.findForIban(iban, typedBic).orElse(null);
        if (matchedBank != null) {
            applyBankProfile(matchedBank);
            setBankProfileReadOnly(true);
            showEndpointStatus(I18n.get("message.fints.bankProfileFound"), "success-copy");
            return;
        }
        setBankProfileReadOnly(false);
        clearBankProfile();
        bicField.setText(typedBic);
        bankCodeField.setText(bankService.bankCodeForGermanIban(iban));
        try {
            BankDirectoryEntry entry = bankService.lookupBankDirectoryEntry(iban);
            if (entry != null) {
                applyDirectoryEntry(entry);
            }
            showEndpointStatus(I18n.get("message.fints.newBankProfile"), "help-text");
        } catch (BankingException e) {
            showEndpointStatus(I18n.get("message.fints.newBankProfile"), "help-text");
        }
    }

    private void clearBankProfile() {
        bankNameField.clear();
        bicField.clear();
        bankCodeField.clear();
        fintsServerUrlField.clear();
        fintsUserIdField.clear();
        fintsCustomerIdField.clear();
        bankCommentField.clear();
    }

    private void applyBankProfile(Bank bank) {
        bankNameField.setText(valueOrEmpty(bank.getName()));
        bicField.setText(valueOrEmpty(bank.getBic()));
        bankCodeField.setText(valueOrEmpty(bank.getBlz()));
        fintsServerUrlField.setText(valueOrEmpty(bank.getFintsServerUrl()));
        fintsUserIdField.setText(valueOrEmpty(bank.getFintsUserId()));
        fintsCustomerIdField.setText(valueOrEmpty(bank.getFintsCustomerId()));
        bankCommentField.setText(valueOrEmpty(bank.getComment()));
    }

    private void applyDirectoryEntry(BankDirectoryEntry entry) {
        if (bankNameField.getText().isBlank()) {
            bankNameField.setText(valueOrEmpty(entry.name()));
        }
        if (bicField.getText().isBlank()) {
            bicField.setText(valueOrEmpty(entry.bic()));
        }
        bankCodeField.setText(valueOrEmpty(entry.blz()));
        if (fintsServerUrlField.getText().isBlank()) {
            fintsServerUrlField.setText(valueOrEmpty(entry.pinTanAddress()));
        }
    }

    private Bank currentBankProfile() {
        if (matchedBank != null) {
            return matchedBank;
        }
        return Bank.builder()
                .name(emptyToNull(bankNameField.getText()))
                .blz(emptyToNull(bankCodeField.getText()))
                .bic(emptyToNull(bicField.getText()))
                .fintsServerUrl(emptyToNull(fintsServerUrlField.getText()))
                .fintsUserId(emptyToNull(fintsUserIdField.getText()))
                .fintsCustomerId(emptyToNull(fintsCustomerIdField.getText()))
                .comment(emptyToNull(bankCommentField.getText()))
                .build();
    }

    protected String promptForPin() {
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

    private void setBankProfileReadOnly(boolean readOnly) {
        bankNameField.setEditable(!readOnly);
        bicField.setEditable(!readOnly);
        fintsServerUrlField.setEditable(!readOnly);
        fintsUserIdField.setEditable(!readOnly);
        fintsCustomerIdField.setEditable(!readOnly);
        bankCommentField.setEditable(!readOnly);
        if (readOnly) {
            bankNameField.getStyleClass().add("read-only-field");
            bicField.getStyleClass().add("read-only-field");
            fintsServerUrlField.getStyleClass().add("read-only-field");
            fintsUserIdField.getStyleClass().add("read-only-field");
            fintsCustomerIdField.getStyleClass().add("read-only-field");
            bankCommentField.getStyleClass().add("read-only-field");
        } else {
            bankNameField.getStyleClass().remove("read-only-field");
            bicField.getStyleClass().remove("read-only-field");
            fintsServerUrlField.getStyleClass().remove("read-only-field");
            fintsUserIdField.getStyleClass().remove("read-only-field");
            fintsCustomerIdField.getStyleClass().remove("read-only-field");
            bankCommentField.getStyleClass().remove("read-only-field");
        }
    }

    private void close() {
        passwordField.clear();
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        FintraxUI.configureDialog(alert);
        alert.showAndWait();
    }

    private void showEndpointStatus(String message, String styleClass) {
        fintsServerStatusLabel.setText(message);
        fintsServerStatusLabel.getStyleClass().removeAll("help-text", "success-copy", "warning-copy", "danger-copy");
        fintsServerStatusLabel.getStyleClass().add(styleClass);
    }

    private String normalizeIban(String iban) {
        return iban == null ? "" : iban.trim().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record AccountDiscoveryResult(boolean connected, List<DiscoveredBankAccount> accounts) {
    }
}
