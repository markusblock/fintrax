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
    private TextField fintsServerUrlField;
    @FXML
    private Label fintsServerStatusLabel;
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
    private void onFindFinTsServer() {
        String iban = normalizeIban(ibanField.getText());
        BankAccount account = BankAccount.builder()
                .iban(iban)
                .bic(emptyToNull(bicField.getText()))
                .accountHolder(accountHolderField.getText().trim())
                .build();
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
        pinField.clear();
        close();
    }

    @FXML
    private void onSave() {
        String iban = normalizeIban(ibanField.getText());
        String bic = emptyToNull(bicField.getText());
        String fintsServerUrl = emptyToNull(fintsServerUrlField.getText());
        String bankName = bankNameField.getText().trim();
        String accountHolder = accountHolderField.getText().trim();
        String pin = pinField.getText();
        String comment = commentField.getText().trim();
        AccountType accountType = accountTypeCombo.getValue();

        if (iban.isEmpty()) {
            showError(I18n.get("validation.iban.required"));
            return;
        }
        if (pin == null || pin.isEmpty()) {
            showError(I18n.get("validation.pin.required"));
            return;
        }

        BankAccount tempAccount = BankAccount.builder()
                .iban(iban)
                .bic(bic)
                .fintsServerUrl(fintsServerUrl)
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
            showError(safeValidationMessage(e));
            return;
        }

        accountService.createAccount(iban, bic, fintsServerUrl, bankName, accountHolder, accountType, comment);

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

    private String safeValidationMessage(BankingException exception) {
        return switch (exception.getFailureReason()) {
            case TAN_REQUIRED -> BankingException.TAN_REQUIRED_MESSAGE;
            case INVALID_IBAN -> I18n.get("message.sync.invalidIban");
            case MISSING_ENDPOINT -> I18n.get("message.sync.endpointMissing");
            case INVALID_ENDPOINT -> I18n.get("message.sync.endpointInvalid");
            case BANK_DIRECTORY_LOOKUP_FAILED -> I18n.get("message.fints.lookupFailed");
            default -> I18n.get("validation.pin.connectionFailed");
        };
    }

    private void showEndpointStatus(String message, String styleClass) {
        fintsServerStatusLabel.setText(message);
        fintsServerStatusLabel.getStyleClass().removeAll("success-copy", "warning-copy", "danger-copy");
        fintsServerStatusLabel.getStyleClass().add(styleClass);
    }

    private String normalizeIban(String iban) {
        return iban == null ? "" : iban.trim().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
