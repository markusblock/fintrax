package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.PinStorage;
import org.fintrax.model.AccountType;
import org.fintrax.model.BankAccount;
import org.fintrax.service.AccountService;
import org.fintrax.ui.FintraxUI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class AddAccountDialogController {
    @FXML
    private TextField ibanField;
    @FXML
    private TextField bicField;
    @FXML
    private TextField bankNameField;
    @FXML
    private TextField accountHolderField;
    @FXML
    private ComboBox<AccountType> accountTypeCombo;
    @FXML
    private PasswordField pinField;
    @FXML
    private TextField commentField;
    @FXML
    private Button saveButton;

    private final AccountService accountService;
    private final BankingProtocol bankingProtocol;

    @Autowired
    public AddAccountDialogController(AccountService accountService, BankingProtocol bankingProtocol) {
        this.accountService = accountService;
        this.bankingProtocol = bankingProtocol;
    }

    public AddAccountDialogController(AccountService accountService, BankingProtocol bankingProtocol,
                                      PinStorage ignoredPinStorage) {
        this(accountService, bankingProtocol);
    }

    @FXML
    public void initialize() {
        accountTypeCombo.getItems().setAll(AccountType.values());
        accountTypeCombo.setValue(AccountType.GIRO);
    }

    @FXML
    private void onCancel() {
        pinField.clear();
        close();
    }

    @FXML
    private void onSave() {
        String iban = ibanField.getText().trim().replaceAll("\\s+", "");
        String bic = bicField.getText().trim();
        String bankName = bankNameField.getText().trim();
        String accountHolder = accountHolderField.getText().trim();
        String pin = pinField.getText();
        String comment = commentField.getText().trim();
        AccountType accountType = accountTypeCombo.getValue();

        if (iban.isEmpty()) {
            showError(I18n.get("validation.iban.required"));
            return;
        }
        if (bic.isEmpty()) {
            showError(I18n.get("validation.bic.required"));
            return;
        }
        if (pin == null || pin.isEmpty()) {
            showError(I18n.get("validation.pin.required"));
            return;
        }

        BankAccount tempAccount = BankAccount.builder()
                .iban(iban)
                .bic(bic)
                .accountHolder(accountHolder)
                .build();

        try {
            boolean valid = bankingProtocol.validatePin(tempAccount, pin);
            if (!valid) {
                showError(I18n.get("validation.pin.invalid"));
                return;
            }
        } catch (BankingException e) {
            log.warn("Bank credential validation failed");
            showError(BankingException.TAN_REQUIRED_MESSAGE.equals(e.getMessage())
                    ? e.getMessage() : I18n.get("validation.pin.connectionFailed"));
            return;
        }

        BankAccount account = accountService.createAccount(iban, bic, bankName, accountHolder, accountType, comment);

        log.info("Added bank account");
        pinField.clear();
        close();
    }

    private void close() {
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
}
