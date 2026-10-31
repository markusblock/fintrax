package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.ui.FintraxUI;
import org.fintrax.fintx.PinStorage;
import org.fintrax.model.BankAccount;
import org.fintrax.service.AccountService;
import org.fintrax.service.ResetService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.RuleService;
import org.fintrax.service.SyncService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.hibiscus.HibiscusXmlImporter;
import org.fintrax.store.ResetGroup;
import org.fintrax.ui.control.TableColumnVisibilityMenu;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
public class SettingsController {
    private static final String CREDENTIAL_COLUMN_CONFIG_KEY = "settings.credentials.columns";

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
    private MenuButton columnMenuButton;
    @FXML
    private TableColumn<CredentialRow, String> accountColumn;
    @FXML
    private TableColumn<CredentialRow, String> statusColumn;

    private final AccountService accountService;
    private final PinStorage pinStorage;
    private final HibiscusXmlImporter hibiscusXmlImporter;
    private final ResetService resetService;
    private final SyncService syncService;
    private final SettingsService settingsService;
    private final RuleService ruleService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final Path storagePath;

    public SettingsController(AccountService accountService, PinStorage pinStorage,
                               HibiscusXmlImporter hibiscusXmlImporter, ResetService resetService,
                               SyncService syncService, SettingsService settingsService,
                               RuleService ruleService, CategoryService categoryService, LabelService labelService,
                               Path storagePath) {
        this.accountService = accountService;
        this.pinStorage = pinStorage;
        this.hibiscusXmlImporter = hibiscusXmlImporter;
        this.resetService = resetService;
        this.syncService = syncService;
        this.settingsService = settingsService;
        this.ruleService = ruleService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.storagePath = storagePath;
    }

    @FXML
    public void initialize() {
        log.info("SettingsController initialized");

        languageCombo.getItems().setAll("English", "Deutsch");
        languageCombo.setValue("de".equals(settingsService.getLanguage()) ? "Deutsch" : "English");
        languageCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                onLanguageChanged(newValue);
            }
        });

        themeCombo.getItems().setAll("Light", "Dark");
        themeCombo.setValue("dark".equals(settingsService.getTheme()) ? "Dark" : "Light");
        themeCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                onThemeChanged(newValue);
            }
        });

        dataDirLabel.setText(storagePath.toString());

        setupColumnMenu();
        loadCredentials();
    }

    private void setupColumnMenu() {
        TableColumnVisibilityMenu.install(credentialTable, columnMenuButton,
                CREDENTIAL_COLUMN_CONFIG_KEY, settingsService,
                List.of(new TableColumnVisibilityMenu.Column("account", accountColumn),
                        new TableColumnVisibilityMenu.Column("status", statusColumn)));
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

    private void onLanguageChanged(String lang) {
        settingsService.saveLanguage("Deutsch".equals(lang) ? "de" : "en");
        I18n.setLocale(settingsService.getLocale());
        refreshMainLocale();
        reloadMainView();
        log.info("Language changed to: {}", lang);
    }

    private void onThemeChanged(String theme) {
        settingsService.saveTheme("Dark".equals(theme) ? "dark" : "light");
        FintraxUI.applyTheme(rootPane.getScene(), settingsService.getTheme());
        log.info("Theme changed to: {}", theme);
    }

    private void reloadMainView() {
        Object controller = rootPane.getScene().getRoot().getProperties().get(MainController.class.getName());
        if (controller instanceof MainController mainController) {
            mainController.reloadCurrentView();
        }
    }

    private void refreshMainLocale() {
        Object controller = rootPane.getScene().getRoot().getProperties().get(MainController.class.getName());
        if (controller instanceof MainController mainController) {
            mainController.refreshLocale();
        }
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
        FintraxUI.configureDialog(dialog);
        dialog.setTitle(I18n.get("settings.import.title"));
        dialog.setHeaderText(I18n.get("settings.import.title"));

        ButtonType importButtonType = new ButtonType(I18n.get("button.import"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importButtonType, ButtonType.CANCEL);

        CheckBox categoriesBox = new CheckBox(I18n.get("settings.import.categories"));
        categoriesBox.setSelected(true);
        CheckBox rulesBox = new CheckBox(I18n.get("settings.import.rules"));
        rulesBox.setSelected(true);

        VBox content = new VBox(10, categoriesBox, rulesBox);
        content.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> buttonType == importButtonType);

        Optional<Boolean> result = dialog.showAndWait();
        if (result.isPresent() && result.get()) {
            try {
                HibiscusXmlImporter.ImportResult importResult = hibiscusXmlImporter.importFile(
                        file, categoriesBox.isSelected(), rulesBox.isSelected());

                showImportSuccess(importResult);

                loadCredentials();
            } catch (Exception e) {
                log.error("Import failed", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                FintraxUI.configureDialog(alert);
                alert.setTitle(I18n.get("settings.import.error", e.getMessage()));
                alert.setHeaderText(I18n.get("settings.import.error", e.getMessage()));
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    void showImportSuccess(HibiscusXmlImporter.ImportResult importResult) {
        Alert report = new Alert(Alert.AlertType.INFORMATION);
        FintraxUI.configureDialog(report);
        report.setTitle(I18n.get("settings.import.success"));
        report.setHeaderText(I18n.get("settings.import.success"));
        report.setContentText(I18n.get("settings.import.result", importResult.toString()));
        report.initOwner(rootPane.getScene().getWindow());

        ButtonType applyRulesType = new ButtonType(
                I18n.get("settings.import.applyRules"), ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType = new ButtonType(I18n.get("button.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        report.getButtonTypes().setAll(applyRulesType, closeType);
        ((Button) report.getDialogPane().lookupButton(applyRulesType)).setId("applyRulesAfterImport");

        report.showAndWait().ifPresent(button -> {
            if (button == applyRulesType) {
                new RuleReviewDialog(rootPane.getScene().getWindow(), ruleService, accountService,
                        categoryService, labelService, settingsService,
                        ruleService::previewAllRules, false).show();
            }
        });
    }

    @FXML
    private void onResetData() {
        if (syncService.isSyncing()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            FintraxUI.configureDialog(alert);
            alert.setTitle(I18n.get("settings.reset.busy"));
            alert.setHeaderText(I18n.get("settings.reset.busy"));
            alert.setContentText(I18n.get("settings.reset.busy.desc"));
            alert.showAndWait();
            return;
        }

        Dialog<Set<ResetGroup>> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.setTitle(I18n.get("settings.reset.title"));
        dialog.setHeaderText(I18n.get("settings.reset.header"));

        ButtonType resetButtonType = new ButtonType(
                I18n.get("button.reset"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(resetButtonType, ButtonType.CANCEL);

        Label warning = new Label(I18n.get("settings.reset.warning"));
        warning.setWrapText(true);
        warning.getStyleClass().add("danger-copy");

        Map<ResetGroup, CheckBox> choices = new LinkedHashMap<>();
        VBox content = new VBox(10, warning);
        content.getStyleClass().add("dialog-content");
        addResetChoice(content, choices, ResetGroup.ACCOUNTS_TRANSACTIONS_HISTORY,
                "settings.reset.accounts");
        addResetChoice(content, choices, ResetGroup.CATEGORIES_RULES_LABELS,
                "settings.reset.categories");
        addResetChoice(content, choices, ResetGroup.STORED_CREDENTIALS,
                "settings.reset.credentials");
        addResetChoice(content, choices, ResetGroup.APPLICATION_SETTINGS,
                "settings.reset.settings");

        HBox selectionButtons = new HBox(10);
        Button selectAll = new Button(I18n.get("button.selectAll"));
        selectAll.setGraphic(icon("fth-check-square", 14));
        selectAll.getStyleClass().add("button-outlined");
        Button clearAll = new Button(I18n.get("button.clearAll"));
        clearAll.setGraphic(icon("fth-x-square", 14));
        clearAll.getStyleClass().add("button-outlined");
        selectionButtons.getChildren().addAll(selectAll, clearAll);
        content.getChildren().add(selectionButtons);
        dialog.getDialogPane().setContent(content);

        Button resetButton = (Button) dialog.getDialogPane().lookupButton(resetButtonType);
        resetButton.setDisable(true);
        Runnable updateResetButton = () -> resetButton.setDisable(
                choices.values().stream().noneMatch(CheckBox::isSelected));
        choices.values().forEach(checkBox -> checkBox.selectedProperty().addListener(
                (observable, oldValue, newValue) -> updateResetButton.run()));
        selectAll.setOnAction(event -> choices.values().forEach(checkBox -> checkBox.setSelected(true)));
        clearAll.setOnAction(event -> choices.values().forEach(checkBox -> checkBox.setSelected(false)));

        dialog.setResultConverter(buttonType -> {
            if (buttonType != resetButtonType) return null;
            return choices.entrySet().stream()
                    .filter(entry -> entry.getValue().isSelected())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        });

        Optional<Set<ResetGroup>> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isEmpty()) return;

        try {
            resetService.reset(result.get());
            loadCredentials();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            FintraxUI.configureDialog(alert);
            alert.setTitle(I18n.get("settings.reset.success"));
            alert.setHeaderText(I18n.get("settings.reset.success"));
            alert.setContentText(I18n.get("settings.reset.result"));
            alert.showAndWait();
        } catch (Exception e) {
            log.error("Reset failed", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            FintraxUI.configureDialog(alert);
            alert.setTitle(I18n.get("settings.reset.error"));
            alert.setHeaderText(I18n.get("settings.reset.error"));
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    private void addResetChoice(VBox content, Map<ResetGroup, CheckBox> choices,
                                ResetGroup group, String labelKey) {
        CheckBox checkBox = new CheckBox(I18n.get(labelKey));
        checkBox.setWrapText(true);
        choices.put(group, checkBox);
        content.getChildren().add(checkBox);
    }

    private FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(literal);
        icon.setIconSize(size);
        return icon;
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
