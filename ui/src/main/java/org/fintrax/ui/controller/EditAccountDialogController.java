package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
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
    private TextField fintsServerUrlField;
    @FXML
    private Label fintsServerStatusLabel;
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
    private final BankingProtocol bankingProtocol;
    private BankAccount account;

    public EditAccountDialogController(AccountService accountService, BankingProtocol bankingProtocol) {
        this.accountService = accountService;
        this.bankingProtocol = bankingProtocol;
    }

    @FXML
    public void initialize() {
        accountTypeCombo.getItems().setAll(AccountType.values());
    }

    public void setAccount(BankAccount account) {
        this.account = account;
        bankNameField.setText(valueOrEmpty(account.getBankName()));
        bicField.setText(valueOrEmpty(account.getBic()));
        fintsServerUrlField.setText(valueOrEmpty(account.getFintsServerUrl()));
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
    private void onFindFinTsServer() {
        try {
            String url = bankingProtocol.lookupPinTanUrl(account);
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
    private void onCancel() {
        close();
    }

    @FXML
    private void onSave() {
        String bic = bicField.getText().trim();
        accountService.updateAccount(account.getId(), bankNameField.getText().trim(), bic.isBlank() ? null : bic,
                fintsServerUrlField.getText().trim(), accountHolderField.getText().trim(),
                accountTypeCombo.getValue(), commentField.getText().trim());
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

    private void showEndpointStatus(String message, String styleClass) {
        fintsServerStatusLabel.setText(message);
        fintsServerStatusLabel.getStyleClass().removeAll("success-copy", "warning-copy", "danger-copy");
        fintsServerStatusLabel.getStyleClass().add(styleClass);
    }

    private void close() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
}
