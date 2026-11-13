package org.fintrax.ui.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fintrax.config.I18n;
import org.fintrax.model.AccountType;
import org.fintrax.model.BankAccount;
import org.fintrax.fintx.DiscoveredBankAccount;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reusable in-place account list and details selector for bank-import flows. */
public final class BankAccountSelectionPane extends VBox {
    private final List<Candidate> candidates = new ArrayList<>();
    private final ReadOnlyIntegerWrapper selectedCount = new ReadOnlyIntegerWrapper();
    private final Label summary = new Label();
    private final ListView<Candidate> accountList = new ListView<>();
    private final VBox details = new VBox(12);

    public BankAccountSelectionPane() {
        super(12);
        getStyleClass().add("content-panel");
        summary.getStyleClass().add("secondary-text");
        details.getStyleClass().add("editor-panel");

        accountList.setPrefWidth(410);
        accountList.setCellFactory(list -> new AccountCell());
        accountList.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> showDetails(selected));
        ScrollPane detailsScroll = new ScrollPane(details);
        detailsScroll.setFitToWidth(true);
        detailsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        detailsScroll.getStyleClass().add("account-scroll");
        SplitPane listAndDetails = new SplitPane(accountList, detailsScroll);
        listAndDetails.setDividerPositions(0.43);
        VBox.setVgrow(listAndDetails, Priority.ALWAYS);
        getChildren().setAll(summary, listAndDetails,
                muted(I18n.get("message.bankAccounts.unavailableHint")),
                muted(I18n.get("message.bankAccounts.resizeHint")));
    }

    public void setAccounts(List<DiscoveredBankAccount> accounts, Set<String> existingIbans) {
        candidates.clear();
        candidates.addAll(createCandidates(accounts, existingIbans));
        accountList.getItems().setAll(candidates);
        candidates.forEach(candidate -> candidate.selected.addListener(
                (observable, oldValue, newValue) -> updateSummary()));
        accountList.getSelectionModel().selectFirst();
        showDetails(accountList.getSelectionModel().getSelectedItem());
        updateSummary();
    }

    public List<DiscoveredBankAccount> selectedAccounts() {
        return candidates.stream()
                .filter(candidate -> candidate.selected.get() && candidate.importable)
                .map(candidate -> candidate.account)
                .toList();
    }

    public ReadOnlyIntegerProperty selectedCountProperty() {
        return selectedCount.getReadOnlyProperty();
    }

    public static String normalizeIban(String iban) {
        return iban == null ? "" : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static String suggestedAccountName(DiscoveredBankAccount account) {
        if (account.accountName() != null && !account.accountName().isBlank()) {
            return account.accountName().trim();
        }
        if (account.bankType() != null && !account.bankType().isBlank()) {
            return account.bankType().trim();
        }
        return "Bank account " + formatIban(account.iban());
    }

    public static AccountType accountTypeFor(DiscoveredBankAccount discovered) {
        String bankType = discovered.bankType() == null ? "" : discovered.bankType().toLowerCase(Locale.ROOT);
        if (bankType.contains("spar") || bankType.contains("saving") || bankType.contains("tagesgeld")) {
            return AccountType.SAVINGS;
        }
        if (bankType.contains("kredit") || bankType.contains("credit") || bankType.contains("karte")) {
            return AccountType.CREDIT_CARD;
        }
        return AccountType.GIRO;
    }

    public static String defaultCurrency(String currency) {
        return currency == null || currency.isBlank() ? "EUR" : currency.trim();
    }

    public static Set<String> existingIbans(List<BankAccount> accounts) {
        Set<String> ibans = new HashSet<>();
        accounts.stream().filter(java.util.Objects::nonNull).map(BankAccount::getIban)
                .map(BankAccountSelectionPane::normalizeIban).filter(iban -> !iban.isBlank())
                .forEach(ibans::add);
        return ibans;
    }

    private List<Candidate> createCandidates(List<DiscoveredBankAccount> accounts, Set<String> existingIbans) {
        Set<String> knownIbans = new HashSet<>();
        existingIbans.stream().map(BankAccountSelectionPane::normalizeIban)
                .filter(iban -> !iban.isBlank()).forEach(knownIbans::add);

        List<Candidate> result = new ArrayList<>(accounts.size());
        for (DiscoveredBankAccount account : accounts) {
            String iban = normalizeIban(account.iban());
            boolean needsManualSetup = !iban.matches("DE\\d{20}");
            boolean duplicate = !iban.isBlank() && !knownIbans.add(iban);
            result.add(new Candidate(account, duplicate, needsManualSetup));
        }
        return result;
    }

    private void showDetails(Candidate candidate) {
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
                        ? I18n.get("label.notProvided") : formatIban(account.iban())),
                detail(I18n.get("label.bic"), display(account.bic())),
                detail(I18n.get("label.blz"), display(account.bankCode())),
                detail(I18n.get("label.accountNumber"), maskAccountNumber(account.accountNumber())),
                detail(I18n.get("label.currency"), display(account.currency())),
                muted(I18n.get(candidate.importable
                        ? "message.bankAccounts.selectHint" : "message.bankAccounts.manualSetupHint")));
    }

    private void updateSummary() {
        long selected = candidates.stream().filter(candidate -> candidate.selected.get()).count();
        long alreadyAdded = candidates.stream().filter(candidate -> candidate.duplicate).count();
        long manual = candidates.stream().filter(candidate -> candidate.needsManualSetup).count();
        summary.setText(I18n.get("message.bankAccounts.selectionSummary", selected, alreadyAdded, manual));
        selectedCount.set((int) selected);
    }

    private VBox detail(String name, String value) {
        Label content = new Label(value);
        content.setWrapText(true);
        return new VBox(3, muted(name), content);
    }

    private static Label heading(String name, String status) {
        Label label = new Label(name + "  ·  " + status);
        label.setWrapText(true);
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

    private static String formatIban(String iban) {
        String normalized = normalizeIban(iban);
        if (normalized.isBlank()) return I18n.get("label.notProvided");
        StringBuilder formatted = new StringBuilder(normalized.length() + normalized.length() / 4);
        for (int index = 0; index < normalized.length(); index++) {
            if (index > 0 && index % 4 == 0) formatted.append(' ');
            formatted.append(normalized.charAt(index));
        }
        return formatted.toString();
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
            importable = !duplicate && !needsManualSetup;
            selected.set(importable);
        }

        private String status() {
            if (duplicate) {
                return I18n.get("message.bankAccounts.alreadyAdded");
            }
            if (needsManualSetup) {
                return I18n.get("message.bankAccounts.manualSetup");
            }
            return I18n.get("message.bankAccounts.newAccount");
        }
    }

    private static final class AccountCell extends ListCell<Candidate> {
        private final CheckBox checkBox = new CheckBox();
        private final VBox text = new VBox(4);
        private final HBox row = new HBox(10, checkBox, text);
        private Candidate candidate;

        private AccountCell() {
            row.setAlignment(Pos.CENTER_LEFT);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
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
            Label iban = muted(formatIban(item.account.iban()));
            iban.setWrapText(true);
            text.getChildren().setAll(heading(suggestedAccountName(item.account), item.status()), iban);
            setGraphic(row);
            setText(null);
        }
    }
}
