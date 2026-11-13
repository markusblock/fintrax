package org.fintrax.ui.controller;

import javafx.scene.Node;
import javafx.scene.control.Button;
import org.fintrax.config.I18n;

final class AccountSetupWizard {
    private final Node connectionPage;
    private final Node selectionPage;
    private final Button backButton;
    private final Button saveButton;
    private final Button primaryButton;
    private final BankAccountSelectionPane selectionPane;
    private final Runnable onShowSelectionPage;
    private final Runnable onShowConnectionPage;
    private boolean showingSelection;

    AccountSetupWizard(Node connectionPage, Node selectionPage, Button backButton, Button saveButton,
                       Button primaryButton, BankAccountSelectionPane selectionPane,
                       Runnable onShowSelectionPage, Runnable onShowConnectionPage) {
        this.connectionPage = connectionPage;
        this.selectionPage = selectionPage;
        this.backButton = backButton;
        this.saveButton = saveButton;
        this.primaryButton = primaryButton;
        this.selectionPane = selectionPane;
        this.onShowSelectionPage = onShowSelectionPage == null ? () -> { } : onShowSelectionPage;
        this.onShowConnectionPage = onShowConnectionPage == null ? () -> { } : onShowConnectionPage;
        showConnectionPage();
    }

    boolean isShowingSelection() {
        return showingSelection;
    }

    void showSelectionPage() {
        showingSelection = true;
        setManagedVisible(connectionPage, false);
        setManagedVisible(selectionPage, true);
        setManagedVisible(backButton, true);
        setManagedVisible(saveButton, false);
        onShowSelectionPage.run();
        updatePrimaryButton();
    }

    void showConnectionPage() {
        showingSelection = false;
        setManagedVisible(selectionPage, false);
        setManagedVisible(connectionPage, true);
        setManagedVisible(backButton, false);
        setManagedVisible(saveButton, true);
        onShowConnectionPage.run();
        updatePrimaryButton();
    }

    void updatePrimaryButton() {
        if (showingSelection) {
            int selected = selectionPane.selectedCountProperty().get();
            primaryButton.setText(I18n.get("button.importSelected", selected));
            primaryButton.setDisable(selected == 0);
        } else {
            primaryButton.setText(I18n.get("button.connect"));
            primaryButton.setDisable(false);
        }
    }

    private void setManagedVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
