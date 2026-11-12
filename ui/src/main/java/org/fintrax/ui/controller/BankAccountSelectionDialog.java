package org.fintrax.ui.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.fintrax.config.I18n;
import org.fintrax.fintx.DiscoveredBankAccount;
import org.fintrax.ui.FintraxUI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** List-and-details selector for accounts returned by a bank's FinTS passport. */
final class BankAccountSelectionDialog {
    private BankAccountSelectionDialog() {
    }

    static List<DiscoveredBankAccount> show(Window owner, List<DiscoveredBankAccount> discoveredAccounts,
                                            Set<String> existingIbans) {
        if (discoveredAccounts.isEmpty()) {
            return List.of();
        }

        List<Candidate> candidates = candidates(discoveredAccounts, existingIbans);
        Dialog<List<DiscoveredBankAccount>> dialog = new Dialog<>();
        dialog.setTitle(I18n.get("dialog.bankAccounts.title"));
        dialog.setHeaderText(I18n.get("dialog.bankAccounts.subtitle"));
        dialog.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        Label summary = new Label();
        summary.getStyleClass().add("secondary-text");
        VBox details = new VBox(12);
        details.getStyleClass().add("editor-panel");
        ListView<Candidate> accountList = new ListView<>();
        accountList.getItems().setAll(candidates);
        accountList.setPrefWidth(410);
        accountList.setCellFactory(list -> new AccountCell());
        accountList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) ->
                showDetails(details, selected));
        accountList.getSelectionModel().selectFirst();
        showDetails(details, accountList.getSelectionModel().getSelectedItem());

        HBox listAndDetails = new HBox(16, accountList, details);
        HBox.setHgrow(details, Priority.ALWAYS);
        VBox content = new VBox(12, summary, listAndDetails,
                muted(I18n.get("message.bankAccounts.unavailableHint")));
        content.setPadding(new Insets(4, 0, 0, 0));
        VBox.setVgrow(listAndDetails, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(980, 600);

        ButtonType importButtonType = new ButtonType(I18n.get("button.importSelected", 0),
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(importButtonType, ButtonType.CANCEL);
        Button importButton = (Button) dialog.getDialogPane().lookupButton(importButtonType);
        importButton.getStyleClass().add("accent-button");

        Runnable updateSummary = () -> {
            long selected = candidates.stream().filter(candidate -> candidate.selected.get()).count();
            long alreadyAdded = candidates.stream().filter(candidate -> candidate.duplicate).count();
            long manual = candidates.stream().filter(candidate -> candidate.needsManualSetup).count();
            summary.setText(I18n.get("message.bankAccounts.selectionSummary", selected, alreadyAdded, manual));
            importButton.setText(I18n.get("button.importSelected", selected));
            importButton.setDisable(selected == 0);
        };
        candidates.forEach(candidate -> candidate.selected.addListener(
                (observable, oldValue, newValue) -> updateSummary.run()));
        dialog.setOnShown(event -> updateSummary.run());
        dialog.setResultConverter(button -> button == importButtonType
                ? candidates.stream().filter(candidate -> candidate.selected.get() && candidate.importable)
                        .map(candidate -> candidate.account).toList()
                : null);

        FintraxUI.configureDialog(dialog);
        return dialog.showAndWait().orElse(List.of());
    }

    static String suggestedAccountName(DiscoveredBankAccount account) {
        if (account.accountName() != null && !account.accountName().isBlank()) {
            return account.accountName().trim();
        }
        if (account.bankType() != null && !account.bankType().isBlank()) {
            return account.bankType().trim();
        }
        return "Bank account " + maskIban(account.iban());
    }

    static String normalizeIban(String iban) {
        return iban == null ? "" : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static List<Candidate> candidates(List<DiscoveredBankAccount> accounts, Set<String> existingIbans) {
        Set<String> knownIbans = new HashSet<>();
        existingIbans.stream().map(BankAccountSelectionDialog::normalizeIban)
                .filter(iban -> !iban.isBlank()).forEach(knownIbans::add);

        List<Candidate> result = new ArrayList<>(accounts.size());
        for (DiscoveredBankAccount account : accounts) {
            String iban = normalizeIban(account.iban());
            boolean needsManualSetup = !iban.matches("DE[0-9]{20}");
            boolean duplicate = !iban.isBlank() && !knownIbans.add(iban);
            result.add(new Candidate(account, duplicate, needsManualSetup));
        }
        return result;
    }

    private static void showDetails(VBox details, Candidate candidate) {
        if (candidate == null) {
            details.getChildren().setAll(muted(I18n.get("message.bankAccounts.chooseAccount")));
            return;
        }
        DiscoveredBankAccount account = candidate.account;
        details.getChildren().setAll(
                heading(suggestedAccountName(account), candidate.status()),
                detail(I18n.get("label.bankAccountType"), display(account.bankType())),
                detail(I18n.get("label.accountHolder"), display(account.accountHolder())),
                detail(I18n.get("label.iban"), account.iban() == null || account.iban().isBlank()
                        ? I18n.get("label.notProvided") : maskIban(account.iban())),
                detail(I18n.get("label.bic"), display(account.bic())),
                detail(I18n.get("label.blz"), display(account.bankCode())),
                detail(I18n.get("label.accountNumber"), maskAccountNumber(account.accountNumber())),
                detail(I18n.get("label.currency"), display(account.currency())),
                muted(I18n.get(candidate.importable
                        ? "message.bankAccounts.selectHint" : "message.bankAccounts.manualSetupHint")));
    }

    private static VBox detail(String name, String value) {
        Label label = muted(name);
        return new VBox(3, label, new Label(value));
    }

    private static Label heading(String name, String status) {
        Label label = new Label(name + "  ·  " + status);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("secondary-text");
        return label;
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String maskIban(String iban) {
        String normalized = normalizeIban(iban);
        return normalized.length() < 8 ? display(normalized)
                : normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }

    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) return "—";
        String normalized = accountNumber.trim();
        return normalized.length() < 4 ? "****" : "****" + normalized.substring(normalized.length() - 4);
    }

    private static final class Candidate {
        private final DiscoveredBankAccount account;
        private final boolean duplicate;
        private final boolean needsManualSetup;
        private final boolean importable;
        private final BooleanProperty selected = new SimpleBooleanProperty();

        private Candidate(DiscoveredBankAccount account, boolean duplicate, boolean needsManualSetup) {
            this.account = account;
            this.duplicate = duplicate;
            this.needsManualSetup = needsManualSetup;
            this.importable = !duplicate && !needsManualSetup;
            this.selected.set(importable);
        }

        private String status() {
            return duplicate ? I18n.get("message.bankAccounts.alreadyAdded")
                    : needsManualSetup ? I18n.get("message.bankAccounts.manualSetup")
                    : I18n.get("message.bankAccounts.newAccount");
        }
    }

    private static final class AccountCell extends ListCell<Candidate> {
        private final CheckBox checkBox = new CheckBox();
        private final VBox text = new VBox(4);
        private final HBox row = new HBox(10, checkBox, text);
        private Candidate candidate;

        private AccountCell() {
            row.setAlignment(Pos.CENTER_LEFT);
            checkBox.setOnAction(event -> {
                if (candidate != null && candidate.importable) {
                    candidate.selected.set(checkBox.isSelected());
                }
            });
        }

        @Override
        protected void updateItem(Candidate item, boolean empty) {
            super.updateItem(item, empty);
            candidate = item;
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            checkBox.setSelected(item.selected.get());
            checkBox.setDisable(!item.importable);
            text.getChildren().setAll(heading(suggestedAccountName(item.account), item.status()),
                    muted(maskIban(item.account.iban()) + " · " + display(item.account.currency())));
            setGraphic(row);
            setText(null);
        }
    }
}
