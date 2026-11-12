package org.fintrax.ui.prototype;

import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.fintrax.ui.FintraxUI;

import java.util.ArrayList;
import java.util.List;

/**
 * Throwaway wizard POC for adding bank accounts and selecting discovered accounts.
 * Uses in-memory sample data only; makes no bank calls and persists nothing.
 */
public final class AccountImportWizardPrototype extends Application {
    private final List<DiscoveredAccount> accounts = new ArrayList<>();
    private final VBox pageHost = new VBox(16);
    private final HBox progress = new HBox(8);
    private final Label pageTitle = new Label();
    private final Label footerStatus = new Label("Sample data only — no bank call or account storage.");
    private final Label selectionSummary = new Label();
    private final Button backButton = new Button("Back");
    private final Button nextButton = new Button();
    private Stage stage;
    private Step currentStep = Step.BANK_CONNECTION;
    private int importedCount;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        accounts.addAll(List.of(
                new DiscoveredAccount("Everyday account", "Girokonto", "Maya Musterfrau",
                        "DE89 3704 0044 0532 0130 00", "COBADEFFXXX", "EUR", false),
                new DiscoveredAccount("Savings account", "Tagesgeld", "Maya Musterfrau",
                        "DE75 5001 0517 0648 4898 90", "INGDDEFFXXX", "EUR", false),
                new DiscoveredAccount("Joint account", "Girokonto", "Maya Musterfrau, Alex Beispiel",
                        "DE44 5001 0517 5407 3249 31", "INGDDEFFXXX", "EUR", true),
                new DiscoveredAccount("Investment settlement", "Verrechnungskonto", "Maya Musterfrau",
                        "", "INGDDEFFXXX", "EUR", false)));
        accounts.forEach(account -> account.selectedProperty().addListener(
                (observable, oldValue, newValue) -> updateSelectionSummary()));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("view-root");
        VBox content = new VBox(18, createHeader(), progress, pageTitle, pageHost, new Separator(), createFooter());
        content.setPadding(new Insets(24));
        VBox.setVgrow(pageHost, Priority.ALWAYS);
        root.setCenter(content);

        Scene scene = new Scene(root, 1060, 740);
        FintraxUI.configureScene(scene);
        FintraxUI.applyTheme(scene, "light");
        stage.setTitle("Fintrax — Add bank account (wizard POC)");
        stage.setMinWidth(900);
        stage.setMinHeight(640);
        stage.setScene(scene);
        renderStep();
        stage.show();
    }

    private Node createHeader() {
        Label title = new Label("Add bank account");
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label("A guided setup for your bank connection and accounts.");
        subtitle.getStyleClass().add("dialog-subtitle");
        Label poc = new Label("POC · SAMPLE DATA");
        poc.getStyleClass().addAll("success-copy", "detail-status-badge", "booked");
        HBox heading = new HBox(14, new VBox(6, title, subtitle), spacer(), poc);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(heading);
        header.getStyleClass().add("editor-panel");
        return header;
    }

    private Node createFooter() {
        backButton.getStyleClass().add("button-outlined");
        backButton.setOnAction(event -> goBack());

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("button-outlined");
        cancelButton.setOnAction(event -> stage.close());

        nextButton.getStyleClass().add("accent-button");
        nextButton.setOnAction(event -> goNext());

        HBox footer = new HBox(10, backButton, footerStatus, spacer(), cancelButton, nextButton);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("view-toolbar");
        return footer;
    }

    private void renderStep() {
        pageTitle.setText(currentStep.title);
        updateProgress();
        pageHost.getChildren().setAll(currentStep == Step.BANK_CONNECTION
                ? createBankConnectionPage() : createAccountSelectionPage());
        backButton.setDisable(currentStep == Step.BANK_CONNECTION || importedCount > 0);
        updateSelectionSummary();
    }

    private Node createBankConnectionPage() {
        GridPane accountFields = fieldsGrid();
        addField(accountFields, 0, "IBAN", new TextField("DE89 3704 0044 0532 0130 00"));
        VBox accountPanel = new VBox(12, new Label("Account details"), accountFields,
                muted("IBAN identifies the account and helps match accounts already in Fintrax."));
        accountPanel.getStyleClass().add("editor-panel");

        GridPane bankFields = fieldsGrid();
        addField(bankFields, 0, "Bank name", new TextField("Musterbank"));
        addField(bankFields, 1, "BIC", new TextField("COBADEFFXXX"));
        addField(bankFields, 2, "BLZ", new TextField("37040044"));
        addField(bankFields, 3, "FinTS User ID", new TextField("demo-user"));
        addField(bankFields, 4, "Customer ID (optional)", new TextField());
        addField(bankFields, 5, "FinTS server URL", new TextField("https://fints.example.test/fints/"));
        PasswordField password = new PasswordField();
        password.setPromptText("Password / PIN (optional)");
        addField(bankFields, 6, "Password / PIN", password);
        CheckBox storePassword = new CheckBox("Store password (unchecked by default)");
        bankFields.add(storePassword, 1, 7);
        VBox bankPanel = new VBox(12, new Label("Bank connection"), bankFields,
                muted("Test connection simulates success and proceeds to account selection."),
                muted("Demo only: no credentials are sent or stored."));
        bankPanel.getStyleClass().add("editor-panel");

        VBox panels = new VBox(14, accountPanel, bankPanel);
        return panels;
    }

    private Node createAccountSelectionPage() {
        ListView<DiscoveredAccount> list = new ListView<>(FXCollections.observableArrayList(accounts));
        list.setPrefWidth(410);
        list.setCellFactory(view -> new ListCell<>() {
            private final CheckBox select = new CheckBox();
            private final VBox text = new VBox(4);
            private final HBox row = new HBox(10, select, text);
            private DiscoveredAccount rowAccount;

            {
                row.setAlignment(Pos.CENTER_LEFT);
                select.setOnAction(event -> {
                    if (rowAccount != null && rowAccount.isImportable()) {
                        rowAccount.selectedProperty().set(select.isSelected());
                    }
                });
            }

            @Override
            protected void updateItem(DiscoveredAccount item, boolean empty) {
                super.updateItem(item, empty);
                rowAccount = item;
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                select.setSelected(item.selectedProperty().get());
                select.setDisable(!item.isImportable());
                text.getChildren().setAll(heading(item.accountName(), item.status()),
                        muted(maskedIban(item.iban()) + " · " + item.currency()));
                setGraphic(row);
                setText(null);
            }
        });

        VBox details = new VBox(12);
        details.getStyleClass().add("editor-panel");
        list.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) ->
                showAccountDetails(details, selected));
        list.getSelectionModel().selectFirst();
        showAccountDetails(details, list.getSelectionModel().getSelectedItem());

        HBox listAndDetails = new HBox(16, list, details);
        HBox.setHgrow(details, Priority.ALWAYS);
        VBox panel = new VBox(12, selectionSummary, listAndDetails,
                muted("Existing accounts and records without an IBAN stay visible but cannot be selected."));
        panel.getStyleClass().add("content-panel");
        VBox.setVgrow(listAndDetails, Priority.ALWAYS);
        return panel;
    }

    private void showAccountDetails(VBox details, DiscoveredAccount account) {
        if (account == null) {
            details.getChildren().setAll(muted("Select an account to see details."));
            return;
        }
        details.getChildren().setAll(
                heading(account.accountName(), account.status()),
                detail("Bank-provided type", account.bankType()),
                detail("Account holder", account.holder()),
                detail("IBAN", account.iban().isBlank() ? "Not provided" : maskedIban(account.iban())),
                detail("BIC", account.bic()), detail("Currency", account.currency()),
                muted(account.isImportable()
                        ? "Select the checkbox to include this account."
                        : "This account needs manual setup or is already in Fintrax."));
    }

    private GridPane fieldsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints(180);
        ColumnConstraints controls = new ColumnConstraints();
        controls.setHgrow(Priority.ALWAYS);
        controls.setFillWidth(true);
        grid.getColumnConstraints().setAll(labels, controls);
        return grid;
    }

    private void addField(GridPane grid, int row, String labelText, Node field) {
        Label label = new Label(labelText);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private VBox detail(String label, String value) {
        Label key = muted(label);
        Label content = new Label(value == null || value.isBlank() ? "—" : value);
        return new VBox(3, key, content);
    }

    private Label heading(String title, String status) {
        Label label = new Label(title + "  ·  " + status);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("secondary-text");
        return label;
    }

    private void importSelected() {
        List<DiscoveredAccount> selected = accounts.stream()
                .filter(account -> account.selectedProperty().get() && account.isImportable())
                .toList();
        selected.forEach(DiscoveredAccount::markImportedInPrototype);
        importedCount += selected.size();
        footerStatus.setText(selected.isEmpty()
                ? "Select at least one account."
                : selected.size() + " imported in prototype only.");
        renderStep();
    }

    private void updateSelectionSummary() {
        long selected = selectedCount();
        long alreadyAdded = accounts.stream().filter(DiscoveredAccount::isAlreadyAdded).count();
        long manual = accounts.stream().filter(account -> !account.hasIban()).count();
        selectionSummary.setText(selected + " selected  ·  " + alreadyAdded + " already added  ·  "
                + manual + " need manual setup");
        if (currentStep == Step.BANK_CONNECTION) {
            nextButton.setText("Connect");
            nextButton.setDisable(false);
        } else if (importedCount > 0) {
            nextButton.setText("Finish");
            nextButton.setDisable(false);
        } else {
            nextButton.setText("Import selected (" + selected + ")");
            nextButton.setDisable(selected == 0);
        }
    }

    private long selectedCount() {
        return accounts.stream().filter(account -> account.selectedProperty().get() && account.isImportable()).count();
    }

    private void goNext() {
        if (currentStep == Step.BANK_CONNECTION) {
            footerStatus.setText("Connection tested (simulated).");
            currentStep = Step.SELECT_ACCOUNTS;
            renderStep();
        } else if (importedCount > 0) {
            stage.close();
        } else {
            importSelected();
        }
    }

    private void goBack() {
        if (currentStep == Step.SELECT_ACCOUNTS && importedCount == 0) {
            currentStep = Step.BANK_CONNECTION;
            renderStep();
        }
    }

    private void updateProgress() {
        progress.getChildren().clear();
        Step[] steps = Step.values();
        for (int index = 0; index < steps.length; index++) {
            Step step = steps[index];
            Label label = new Label((index + 1) + "  " + step.shortTitle);
            label.getStyleClass().add("detail-status-badge");
            if (step == currentStep) label.getStyleClass().add("booked");
            progress.getChildren().add(label);
            if (index < steps.length - 1) {
                Label arrow = new Label("›");
                arrow.getStyleClass().add("secondary-text");
                progress.getChildren().add(arrow);
            }
        }
    }

    private String maskedIban(String iban) {
        if (iban == null || iban.isBlank()) return "IBAN not provided";
        String normalized = iban.replaceAll("\\s+", "");
        return normalized.length() < 8 ? normalized
                : normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private enum Step {
        BANK_CONNECTION("Bank and connection", "Bank connection"),
        SELECT_ACCOUNTS("Select accounts to import", "Select accounts");

        private final String title;
        private final String shortTitle;

        Step(String title, String shortTitle) {
            this.title = title;
            this.shortTitle = shortTitle;
        }
    }

    private static final class DiscoveredAccount {
        private final String accountName;
        private final String bankType;
        private final String holder;
        private final String iban;
        private final String bic;
        private final String currency;
        private final boolean alreadyAdded;
        private final BooleanProperty selected = new SimpleBooleanProperty();
        private boolean importedInPrototype;

        private DiscoveredAccount(String accountName, String bankType, String holder, String iban,
                                  String bic, String currency, boolean alreadyAdded) {
            this.accountName = accountName;
            this.bankType = bankType;
            this.holder = holder;
            this.iban = iban;
            this.bic = bic;
            this.currency = currency;
            this.alreadyAdded = alreadyAdded;
            selected.set(!alreadyAdded && iban != null && !iban.isBlank());
        }

        private String accountName() { return accountName; }
        private String bankType() { return bankType; }
        private String holder() { return holder; }
        private String iban() { return iban; }
        private String bic() { return bic; }
        private String currency() { return currency; }
        private BooleanProperty selectedProperty() { return selected; }
        private boolean isAlreadyAdded() { return alreadyAdded; }
        private boolean hasIban() { return iban != null && !iban.isBlank(); }
        private boolean isImportable() { return !alreadyAdded && !importedInPrototype && hasIban(); }

        private String status() {
            if (importedInPrototype) return "Imported (prototype)";
            if (alreadyAdded) return "Already added";
            return hasIban() ? "New" : "Needs manual setup";
        }

        private void markImportedInPrototype() {
            importedInPrototype = true;
            selected.set(false);
        }
    }
}
