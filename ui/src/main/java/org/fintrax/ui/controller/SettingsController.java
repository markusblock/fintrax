package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.PinStorage;
import org.fintrax.model.BankAccount;
import org.fintrax.service.AccountService;
import org.fintrax.service.ServiceRegistry;
import org.fintrax.service.hibiscus.HibiscusXmlImporter;
import org.fintrax.store.StoragePathResolver;

import java.io.File;
import java.util.List;
import java.util.Optional;

@Slf4j
public class SettingsController {
    @FXML
    private BorderPane rootPane;
    @FXML
    private ComboBox<String> languageCombo;
    @FXML
    private ComboBox<String> themeCombo;
    @FXML
    private Label dataDirLabel;
    @FXML
    private TableView<CredentialRow> credentialTable;
    @FXML
    private TableColumn<CredentialRow, String> accountColumn;
    @FXML
    private TableColumn<CredentialRow, String> statusColumn;

    private final AccountService accountService = ServiceRegistry.getInstance().getAccountService();
    private final PinStorage pinStorage = ServiceRegistry.getInstance().getPinStorage();
    private final HibiscusXmlImporter hibiscusXmlImporter = ServiceRegistry.getInstance().getHibiscusXmlImporter();

    @FXML
    public void initialize() {
        log.info("SettingsController initialized");

        languageCombo.getItems().setAll("English", "Deutsch");
        languageCombo.setValue("English");

        themeCombo.getItems().setAll("Light", "Dark");
        themeCombo.setValue("Light");

        dataDirLabel.setText(StoragePathResolver.resolve().toString());

        loadCredentials();
    }

    private void loadCredentials() {
        List<BankAccount> accounts = accountService.getAllAccounts();
        credentialTable.getItems().clear();

        for (BankAccount account : accounts) {
            String pin = pinStorage.retrievePin(String.valueOf(account.getId()));
            String status = pin != null ? I18n.get("settings.credential.stored") : I18n.get("settings.credential.missing");
            credentialTable.getItems().add(new CredentialRow(account.getId(), account.getBankName(), status));
        }
    }

    @FXML
    private void onDeleteCredential() {
        CredentialRow selected = credentialTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        pinStorage.deletePin(String.valueOf(selected.accountId));
        loadCredentials();
        log.info("Deleted credential for account {}", selected.accountId);
    }

    @FXML
    private void onSaveLanguage() {
        String lang = languageCombo.getValue();
        log.info("Language changed to: {}", lang);
    }

    @FXML
    private void onSaveTheme() {
        String theme = themeCombo.getValue();
        log.info("Theme changed to: {}", theme);
    }

    @FXML
    private void onImportHibiscus() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18n.get("settings.import.title"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Hibiscus XML", "*.xml"));
        File file = fileChooser.showOpenDialog(rootPane.getScene().getWindow());

        if (file == null) return;

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(I18n.get("settings.import.title"));
        dialog.setHeaderText(I18n.get("settings.import.title"));

        ButtonType importButtonType = new ButtonType(I18n.get("button.import"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importButtonType, ButtonType.CANCEL);

        CheckBox categoriesBox = new CheckBox(I18n.get("settings.import.categories"));
        categoriesBox.setSelected(true);
        CheckBox rulesBox = new CheckBox(I18n.get("settings.import.rules"));
        rulesBox.setSelected(true);

        VBox content = new VBox(10, categoriesBox, rulesBox);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> buttonType == importButtonType);

        Optional<Boolean> result = dialog.showAndWait();
        if (result.isPresent() && result.get()) {
            try {
                HibiscusXmlImporter.ImportResult importResult = hibiscusXmlImporter.importFile(
                        file, categoriesBox.isSelected(), rulesBox.isSelected());

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(I18n.get("settings.import.success"));
                alert.setHeaderText(I18n.get("settings.import.success"));
                alert.setContentText(I18n.get("settings.import.result", importResult.toString()));
                alert.showAndWait();

                loadCredentials();
            } catch (Exception e) {
                log.error("Import failed", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(I18n.get("settings.import.error", e.getMessage()));
                alert.setHeaderText(I18n.get("settings.import.error", e.getMessage()));
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    public static class CredentialRow {
        public final Long accountId;
        public final String accountName;
        public final String status;

        public CredentialRow(Long accountId, String accountName, String status) {
            this.accountId = accountId;
            this.accountName = accountName;
            this.status = status;
        }

        public String getAccountName() { return accountName; }
        public String getStatus() { return status; }
    }
}
