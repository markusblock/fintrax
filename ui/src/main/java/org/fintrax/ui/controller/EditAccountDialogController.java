package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.AccountType;
import org.fintrax.model.BankAccount;
import org.fintrax.service.AccountService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@Scope("prototype")
public class EditAccountDialogController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private TextField bankNameField;
    @FXML
    private TextField bicField;
    @FXML
    private TextField accountHolderField;
    @FXML
    private ComboBox<AccountType> accountTypeCombo;
    @FXML
    private TextField ibanField;
    @FXML
    private TextField currencyField;
    @FXML
    private TextArea commentField;
    @FXML
    private Label balanceValue;
    @FXML
    private Label balanceDateValue;
    @FXML
    private Label lastSyncValue;
    @FXML
    private Button saveButton;

    private final AccountService accountService;
    private BankAccount account;

    public EditAccountDialogController(AccountService accountService) {
        this.accountService = accountService;
    }

    @FXML
    public void initialize() {
        accountTypeCombo.getItems().setAll(AccountType.values());
    }

    public void setAccount(BankAccount account) {
        this.account = account;
        bankNameField.setText(valueOrEmpty(account.getBankName()));
        bicField.setText(valueOrEmpty(account.getBic()));
        accountHolderField.setText(valueOrEmpty(account.getAccountHolder()));
        accountTypeCombo.setValue(account.getAccountType());
        ibanField.setText(valueOrEmpty(account.getIban()));
        currencyField.setText(valueOrEmpty(account.getCurrency()));
        commentField.setText(valueOrEmpty(account.getComment()));
        balanceValue.setText(formatBalance(account));
        balanceDateValue.setText(account.getBalanceDate() == null
                ? "—" : account.getBalanceDate().format(DATE_TIME_FORMAT));
        lastSyncValue.setText(account.getLastSyncAt() == null
                ? I18n.get("label.neverSynced") : account.getLastSyncAt().format(DATE_TIME_FORMAT));
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onSave() {
        accountService.updateAccount(account.getId(), bankNameField.getText().trim(), bicField.getText().trim(),
                accountHolderField.getText().trim(), accountTypeCombo.getValue(), commentField.getText().trim());
        close();
    }

    private String formatBalance(BankAccount bankAccount) {
        if (bankAccount.getBalance() == null) return "—";
        return String.format("%,.2f %s", bankAccount.getBalance().doubleValue(),
                valueOrEmpty(bankAccount.getCurrency()));
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void close() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
}
