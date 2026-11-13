package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankDirectoryEntry;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.DiscoveredBankAccount;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;
import org.fintrax.service.AccountService;
import org.fintrax.service.ActivityLogger;
import org.fintrax.service.BankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Scope("prototype")
public class AddAccountDialogController extends BankAccountDialogBase {
    private static final String READ_ONLY_FIELD_STYLE_CLASS = "read-only-field";
    private static final String INVALID_IBAN_MESSAGE_KEY = "message.sync.invalidIban";
    private static final String CONNECTION_TEST_SUCCESS_MESSAGE_KEY = "message.fints.connectionTestSuccess";

    @FXML
    private TextField ibanField;
    @FXML
    private TextField bicField;
    @FXML
    private TextField bankCodeField;
    @FXML
    private Label wizardStepLabel;
    private Bank matchedBank;
    private String checkedIban;

    @Autowired
    public AddAccountDialogController(AccountService accountService, BankingProtocol bankingProtocol,
                                      BankService bankService, ActivityLogger activityLogger) {
        super(accountService, bankingProtocol, bankService, activityLogger);
    }

    @FXML
    public void initialize() {
        bankCodeField.setEditable(false);
        ibanField.textProperty().addListener((observable, oldValue, newValue) -> refreshBankProfileForIban());
        initializeAccountWizard(showingSelection -> wizardStepLabel.setText(
                I18n.get(showingSelection ? "wizard.addAccount.accountsStep" : "wizard.addAccount.bankStep")));
    }

    @FXML
    private void onFindFinTsServer() {
        FinTsEndpointLookup.Result lookup = FinTsEndpointLookup.find(bankService, normalizeIban(ibanField.getText()),
                fintsServerUrlField.getText(), matchedBank != null);
        if (lookup.found() && matchedBank == null && !lookup.manualEndpoint()) {
            applyDirectoryEntry(lookup.entry());
        }
        showEndpointStatus(I18n.get(lookup.messageKey()),
                lookup.found() ? SUCCESS_COPY_STYLE_CLASS : WARNING_COPY_STYLE_CLASS);
    }

    @Override
    protected void onTestConnection() {
        Bank bank = currentBankProfile();
        String bankName = bankName(bank);
        String pin = pinForConnection(bankName);
        if (pin == null) return;
        String iban = normalizeIban(ibanField.getText());
        BankAccount account = BankAccount.builder().iban(iban).build();
        startConnectionTest(account, bank, pin, null, bankName);
    }

    @FXML
    protected void onPrimaryAction() { super.onPrimaryAction(); }

    @FXML
    protected void onBack() { super.onBack(); }

    @FXML
    protected void onCancel() { super.onCancel(); }

    @Override
    protected void importSelectedAccounts() {
        List<DiscoveredBankAccount> selected = accountSelectionPane.selectedAccounts();
        if (!selected.isEmpty()) {
            importDiscoveredAccounts(selected, normalizeIban(ibanField.getText()), bicField.getText());
        }
    }

    private void importDiscoveredAccounts(List<DiscoveredBankAccount> discovered,
                                          String sourceIban, String sourceBic) {
        try {
            Bank bank = ensureBankProfile(sourceIban, sourceBic);
            if (bank == null) return;
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
            showError(I18n.get(INVALID_IBAN_MESSAGE_KEY));
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

    @FXML
    private void onSave() {
        String iban = normalizeIban(ibanField.getText());
        String bic = emptyToNull(bicField.getText());
        if (iban.isEmpty()) {
            showError(I18n.get("validation.iban.required"));
            return;
        }

        try {
            saveBankProfile(iban, bic, bankService.lookupBankDirectoryEntry(iban));
        } catch (BankingException e) {
            String messageKey = e.getFailureReason() == BankingException.FailureReason.INVALID_IBAN
                    ? INVALID_IBAN_MESSAGE_KEY : "message.fints.lookupFailed";
            showError(I18n.get(messageKey));
        }
    }

    private void saveBankProfile(String iban, String bic, BankDirectoryEntry directoryEntry) {
        String bankCode = directoryEntry == null
                ? bankService.bankCodeForGermanIban(iban) : directoryEntry.blz();
        if (bankCode == null) {
            showError(I18n.get(INVALID_IBAN_MESSAGE_KEY));
            return;
        }

        matchedBank = bankService.findForIban(iban, bic).orElse(matchedBank);
        String bankName = emptyToNull(bankNameField.getText());
        if (bankName == null && directoryEntry != null) bankName = emptyToNull(directoryEntry.name());
        String userId = emptyToNull(fintsUserIdField.getText());
        if (bankName == null || userId == null) {
            showError(I18n.get(bankName == null ? "validation.bankName.required" : "validation.fintsUserId.required"));
            return;
        }
        if (bic == null && directoryEntry != null) bic = emptyToNull(directoryEntry.bic());
        String fintsServerUrl = emptyToNull(fintsServerUrlField.getText());
        if (fintsServerUrl == null && directoryEntry != null) {
            fintsServerUrl = emptyToNull(directoryEntry.pinTanAddress());
        }
        String customerId = emptyToNull(fintsCustomerIdField.getText());
        String comment = emptyToNull(bankCommentField.getText());
        if (matchedBank == null) {
            matchedBank = bankService.createBank(bankCode, bankName, bic, fintsServerUrl, userId, customerId, comment);
        } else {
            matchedBank = bankService.updateBank(matchedBank.getId(), bankName, bic, fintsServerUrl,
                    userId, customerId, comment);
        }

        log.info("Saved bank connection details for {}", matchedBank.getName());
        close();
    }

    private void refreshBankProfileForIban() {
        String iban = normalizeIban(ibanField.getText());
        if (!iban.matches("DE\\d{20}")) {
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
            showEndpointStatus(I18n.get("message.fints.bankProfileFound"), SUCCESS_COPY_STYLE_CLASS);
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
            showEndpointStatus(I18n.get("message.fints.newBankProfile"), HELP_TEXT_STYLE_CLASS);
        } catch (BankingException e) {
            showEndpointStatus(I18n.get("message.fints.newBankProfile"), HELP_TEXT_STYLE_CLASS);
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

    private void setBankProfileReadOnly(boolean readOnly) {
        bankNameField.setEditable(!readOnly);
        bicField.setEditable(!readOnly);
        fintsServerUrlField.setEditable(!readOnly);
        fintsUserIdField.setEditable(!readOnly);
        fintsCustomerIdField.setEditable(!readOnly);
        bankCommentField.setEditable(!readOnly);
        if (readOnly) {
            bankNameField.getStyleClass().add(READ_ONLY_FIELD_STYLE_CLASS);
            bicField.getStyleClass().add(READ_ONLY_FIELD_STYLE_CLASS);
            fintsServerUrlField.getStyleClass().add(READ_ONLY_FIELD_STYLE_CLASS);
            fintsUserIdField.getStyleClass().add(READ_ONLY_FIELD_STYLE_CLASS);
            fintsCustomerIdField.getStyleClass().add(READ_ONLY_FIELD_STYLE_CLASS);
            bankCommentField.getStyleClass().add(READ_ONLY_FIELD_STYLE_CLASS);
        } else {
            bankNameField.getStyleClass().remove(READ_ONLY_FIELD_STYLE_CLASS);
            bicField.getStyleClass().remove(READ_ONLY_FIELD_STYLE_CLASS);
            fintsServerUrlField.getStyleClass().remove(READ_ONLY_FIELD_STYLE_CLASS);
            fintsUserIdField.getStyleClass().remove(READ_ONLY_FIELD_STYLE_CLASS);
            fintsCustomerIdField.getStyleClass().remove(READ_ONLY_FIELD_STYLE_CLASS);
            bankCommentField.getStyleClass().remove(READ_ONLY_FIELD_STYLE_CLASS);
        }
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

}
