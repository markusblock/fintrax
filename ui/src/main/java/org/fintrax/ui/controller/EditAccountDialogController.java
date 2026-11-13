package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.DiscoveredBankAccount;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;
import org.fintrax.model.AccountType;
import org.fintrax.service.AccountService;
import org.fintrax.service.ActivityLogger;
import org.fintrax.service.BankService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@Scope("prototype")
public class EditAccountDialogController extends BankAccountDialogBase {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
    private BankAccount account;
    private Bank bank;

    public EditAccountDialogController(AccountService accountService, BankService bankService,
                                       BankingProtocol bankingProtocol, ActivityLogger activityLogger) {
        super(accountService, bankingProtocol, bankService, activityLogger);
    }

    @FXML
    public void initialize() {
        accountTypeCombo.getItems().setAll(AccountType.values());
        initializeAccountWizard(null);
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
        FinTsEndpointLookup.Result lookup = FinTsEndpointLookup.find(bankService, account.getIban(),
                fintsServerUrlField.getText(), false);
        if (lookup.found() && !lookup.manualEndpoint()) {
            fintsServerUrlField.setText(lookup.endpoint());
        }
        showEndpointStatus(I18n.get(lookup.messageKey()),
                lookup.found() ? SUCCESS_COPY_STYLE_CLASS : WARNING_COPY_STYLE_CLASS);
    }

    @Override
    protected void onTestConnection() {
        Bank testBank = bankProfileFromFields();
        String displayName = bankName(testBank);
        String pin = pinForConnection(displayName);
        if (pin == null) return;
        startConnectionTest(account, testBank, pin, account.getId(), displayName);
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

}
