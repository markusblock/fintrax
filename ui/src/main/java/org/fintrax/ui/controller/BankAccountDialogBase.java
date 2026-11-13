package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

abstract class BankAccountDialogBase {
    @FXML
    protected Button saveButton;
    @FXML
    protected Button testConnectionButton;
    @FXML
    protected Button backButton;
    @FXML
    protected ScrollPane connectionPage;
    @FXML
    protected VBox accountSelectionPage;
    @FXML
    protected VBox accountSelectionHost;
    @FXML
    protected Label accountImportStatusLabel;

    protected BankAccountSelectionPane accountSelectionPane;
    protected AccountSetupWizard wizard;

    protected final void initializeAccountWizard(AccountSetupWizard.PageChangeListener pageChanged) {
        accountSelectionPane = new BankAccountSelectionPane();
        accountSelectionHost.getChildren().setAll(accountSelectionPane);
        VBox.setVgrow(accountSelectionPane, javafx.scene.layout.Priority.ALWAYS);
        wizard = new AccountSetupWizard(connectionPage, accountSelectionPage, backButton, saveButton,
                testConnectionButton, accountSelectionPane, pageChanged);
        accountSelectionPane.selectedCountProperty().addListener(
                (observable, oldValue, newValue) -> wizard.updatePrimaryButton());
    }
}
