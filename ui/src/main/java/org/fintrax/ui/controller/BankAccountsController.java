package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.PinStorage;
import org.fintrax.model.BankAccount;
import org.fintrax.model.SyncLog;
import org.fintrax.model.SyncStatus;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.RuleService;
import org.fintrax.service.SyncResult;
import org.fintrax.service.SyncService;
import org.fintrax.ui.ViewLoader;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@Scope("prototype")
public class BankAccountsController {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private BorderPane rootPane;
    @FXML
    private Button addAccountButton;
    @FXML
    private Button syncAllButton;
    @FXML
    private Label subtitleLabel;
    @FXML
    private FlowPane accountsPane;
    @FXML
    private VBox syncStatusPane;
    @FXML
    private ScrollPane syncLogScroll;
    @FXML
    private VBox syncLogContainer;

    private final AccountService accountService;
    private final SyncService syncService;
    private final RuleService ruleService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final PinStorage pinStorage;
    private final ViewLoader viewLoader;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sync-worker");
        t.setDaemon(true);
        return t;
    });

    public BankAccountsController(AccountService accountService, SyncService syncService,
                                  PinStorage pinStorage, ViewLoader viewLoader, RuleService ruleService,
                                  CategoryService categoryService, LabelService labelService) {
        this.accountService = accountService;
        this.syncService = syncService;
        this.pinStorage = pinStorage;
        this.viewLoader = viewLoader;
        this.ruleService = ruleService;
        this.categoryService = categoryService;
        this.labelService = labelService;
    }

    @FXML
    public void initialize() {
        log.info("BankAccountsController initialized");
        loadAccounts();
        loadSyncLogs();
    }

    private void loadAccounts() {
        accountsPane.getChildren().clear();
        List<BankAccount> accounts = accountService.getAllAccounts();

        if (accounts.isEmpty()) {
            Label emptyLabel = new Label(I18n.get("message.noAccounts"));
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #888;");
            emptyLabel.setPadding(new Insets(40));
            accountsPane.getChildren().add(emptyLabel);
            return;
        }

        for (BankAccount account : accounts) {
            VBox card = createAccountCard(account);
            accountsPane.getChildren().add(card);
        }
    }

    private VBox createAccountCard(BankAccount account) {
        VBox card = new VBox(8);
        card.getStyleClass().add("account-card");
        card.setPrefWidth(280);
        card.setPadding(new Insets(15));

        HBox header = new HBox(8);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label bankNameLabel = new Label(account.getBankName() != null ? account.getBankName() : "Unknown Bank");
        bankNameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label typeLabel = new Label(account.getAccountType() != null ? account.getAccountType().name() : "");
        typeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        header.getChildren().addAll(bankNameLabel, spacer, typeLabel);

        Label holderLabel = new Label(account.getAccountHolder() != null ? account.getAccountHolder() : "");
        holderLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        String maskedIban = maskIban(account.getIban());
        Label ibanLabel = new Label(maskedIban);
        ibanLabel.setStyle("-fx-font-size: 12px; -fx-font-family: monospace;");

        Label balanceLabel = new Label(formatBalance(account.getBalance()));
        balanceLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;" +
                (account.getBalance() != null && account.getBalance().signum() < 0 ? " -fx-text-fill: #d32f2f;" : " -fx-text-fill: #2e7d32;"));

        String lastSyncText = account.getLastSyncAt() != null
                ? I18n.get("label.lastSync") + ": " + account.getLastSyncAt().format(DATE_FMT)
                : I18n.get("label.neverSynced");
        Label lastSyncLabel = new Label(lastSyncText);
        lastSyncLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Button syncButton = new Button(I18n.get("button.sync"));
        syncButton.getStyleClass().add("sync-button");
        syncButton.setOnAction(e -> onSyncAccount(account.getId()));

        Button deleteButton = new Button(I18n.get("button.delete"));
        deleteButton.getStyleClass().add("delete-button");
        deleteButton.setOnAction(e -> onDeleteAccount(account));

        buttonBox.getChildren().addAll(syncButton, deleteButton);

        card.getChildren().addAll(header, holderLabel, ibanLabel, balanceLabel, lastSyncLabel, buttonBox);
        return card;
    }

    private String maskIban(String iban) {
        if (iban == null || iban.length() < 8) return iban != null ? iban : "";
        return iban.substring(0, 4) + " **** **** " + iban.substring(iban.length() - 4);
    }

    private String formatBalance(BigDecimal balance) {
        if (balance == null) return "0.00 EUR";
        return String.format("%,.2f EUR", balance.doubleValue());
    }

    @FXML
    private void onAddAccount() {
        try {
            Parent root = viewLoader.load("addAccountDialog");

            Stage dialog = new Stage();
            dialog.setTitle(I18n.get("dialog.addAccount.title"));
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setScene(new Scene(root, 450, 500));

            Window ownerWindow = addAccountButton.getScene().getWindow();
            dialog.initOwner(ownerWindow);

            dialog.showAndWait();
            loadAccounts();
        } catch (Exception e) {
            log.error("Failed to open add account dialog", e);
            showError(I18n.get("error.dialog.open", e.getMessage()));
        }
    }

    @FXML
    private void onSyncAll() {
        List<BankAccount> accounts = accountService.getAllAccounts();
        Map<Long, String> accountPins = new LinkedHashMap<>();
        for (BankAccount account : accounts) {
            String pin = pinStorage.retrievePin(String.valueOf(account.getId()));
            if (pin == null) {
                pin = showPinDialog(account.getId());
            }
            if (pin != null) {
                accountPins.put(account.getId(), pin);
            }
        }
        if (accountPins.isEmpty()) return;

        executor.submit(() -> {
            List<SyncResult> results = new ArrayList<>();
            Map<Long, String> failures = new LinkedHashMap<>();
            accountPins.forEach((accountId, pin) -> {
                try {
                    results.add(syncService.syncAccount(accountId, pin));
                } catch (Exception e) {
                    log.error("Sync failed for account {}", accountId, e);
                    failures.put(accountId, errorMessage(e));
                }
            });
            Platform.runLater(() -> {
                loadAccounts();
                loadSyncLogs();
                showSyncReport(results, failures);
            });
        });
    }

    private void onSyncAccount(Long accountId) {
        String pin = pinStorage.retrievePin(String.valueOf(accountId));

        if (pin == null) {
            pin = showPinDialog(accountId);
            if (pin == null) return;
        }

        final String finalPin = pin;
        executor.submit(() -> {
            try {
                SyncResult syncResult = syncService.syncAccount(accountId, finalPin);
                Platform.runLater(() -> {
                    loadAccounts();
                    loadSyncLogs();
                    showSyncReport(List.of(syncResult), Map.of());
                });
            } catch (Exception e) {
                log.error("Sync failed for account {}", accountId, e);
                Platform.runLater(() -> {
                    loadSyncLogs();
                    showSyncReport(List.of(), Map.of(accountId, errorMessage(e)));
                });
            }
        });
    }

    private String showPinDialog(Long accountId) {
        Optional<BankAccount> accountOpt = accountService.getAccount(accountId);
        if (accountOpt.isEmpty()) return null;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(I18n.get("dialog.pin.title"));
        dialog.setHeaderText(I18n.get("dialog.pin.header", accountOpt.get().getBankName()));
        dialog.setContentText(I18n.get("dialog.pin.prompt"));
        dialog.initModality(Modality.APPLICATION_MODAL);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().addAll(rootPane.getScene().getStylesheets());

        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private void onDeleteAccount(BankAccount account) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.get("dialog.deleteAccount.title"));
        alert.setHeaderText(I18n.get("dialog.deleteAccount.header"));
        alert.setContentText(I18n.get("dialog.deleteAccount.content", account.getBankName(), account.getIban()));
        alert.getDialogPane().getStylesheets().addAll(rootPane.getScene().getStylesheets());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            accountService.deleteAccount(account.getId());
            pinStorage.deletePin(String.valueOf(account.getId()));
            loadAccounts();
        }
    }

    private void loadSyncLogs() {
        syncLogContainer.getChildren().clear();
        List<BankAccount> accounts = accountService.getAllAccounts();

        for (BankAccount account : accounts) {
            List<SyncLog> logs = syncService.getSyncLogs(account.getId());
            if (!logs.isEmpty()) {
                SyncLog latest = logs.get(logs.size() - 1);
                HBox row = createSyncLogRow(account, latest);
                syncLogContainer.getChildren().add(row);
            }
        }

        if (syncLogContainer.getChildren().isEmpty()) {
            Label noLogs = new Label(I18n.get("message.noSyncLogs"));
            noLogs.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");
            syncLogContainer.getChildren().add(noLogs);
        }
    }

    private HBox createSyncLogRow(BankAccount account, SyncLog syncLog) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 5, 3, 5));

        String statusIcon = switch (syncLog.getStatus()) {
            case SUCCESS -> "\u2705";
            case FAILED -> "\u274C";
            case PARTIAL -> "\u26A0\uFE0F";
        };

        Label statusLabel = new Label(statusIcon);
        Label accountLabel = new Label(account.getBankName() != null ? account.getBankName() : account.getIban());
        accountLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        String message;
        if (syncLog.getStatus() == SyncStatus.FAILED) {
            message = I18n.get("message.sync.failed", syncLog.getErrorMessage() != null ? syncLog.getErrorMessage() : "Unknown error");
        } else {
            message = I18n.get("message.sync.success", syncLog.getNewCount(), syncLog.getSkippedCount());
        }
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String timeText = syncLog.getCompletedAt() != null ? syncLog.getCompletedAt().format(DATE_FMT) : "";
        Label timeLabel = new Label(timeText);
        timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        row.getChildren().addAll(statusLabel, accountLabel, messageLabel, spacer, timeLabel);

        if (syncLog.getStatus() == SyncStatus.FAILED) {
            row.setStyle("-fx-background-color: #fff3f3;");
        }

        return row;
    }

    void showSyncReport(List<SyncResult> results, Map<Long, String> failures) {
        int newCount = results.stream().filter(result -> result.syncLog().getStatus() == SyncStatus.SUCCESS)
                .mapToInt(result -> result.syncLog().getNewCount()).sum();
        int skippedCount = results.stream().filter(result -> result.syncLog().getStatus() == SyncStatus.SUCCESS)
                .mapToInt(result -> result.syncLog().getSkippedCount()).sum();
        List<Long> newTransactionIds = results.stream()
                .filter(result -> result.syncLog().getStatus() == SyncStatus.SUCCESS)
                .flatMap(result -> result.newTransactionIds().stream()).toList();

        List<String> details = new ArrayList<>();
        for (SyncResult result : results) {
            SyncLog syncLog = result.syncLog();
            String accountName = accountName(syncLog.getBankAccountId());
            if (syncLog.getStatus() == SyncStatus.FAILED) {
                details.add(I18n.get("message.sync.report.failure", accountName, syncLog.getErrorMessage()));
            } else {
                details.add(I18n.get("message.sync.report.account", accountName,
                        syncLog.getNewCount(), syncLog.getSkippedCount()));
            }
        }
        failures.forEach((accountId, error) -> details.add(
                I18n.get("message.sync.report.failure", accountName(accountId), error)));

        boolean hasFailures = !failures.isEmpty() || results.stream()
                .anyMatch(result -> result.syncLog().getStatus() == SyncStatus.FAILED);
        Alert report = new Alert(hasFailures ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION);
        report.setTitle(I18n.get("message.sync.report.title"));
        report.setHeaderText(I18n.get("message.sync.report.title"));
        String summary = I18n.get("message.sync.report.summary", newCount, skippedCount);
        report.setContentText(details.isEmpty() ? summary : summary + "\n" + String.join("\n", details));
        report.initOwner(rootPane.getScene().getWindow());

        ButtonType closeType = new ButtonType(I18n.get("button.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        if (newTransactionIds.isEmpty()) {
            report.getButtonTypes().setAll(closeType);
        } else {
            ButtonType applyRulesType = new ButtonType(
                    I18n.get("button.applyRulesToNewTransactions"), ButtonBar.ButtonData.OK_DONE);
            report.getButtonTypes().setAll(applyRulesType, closeType);
            ((Button) report.getDialogPane().lookupButton(applyRulesType)).setId("applyRulesToNewTransactions");
            report.showAndWait().ifPresent(button -> {
                if (button == applyRulesType) {
                    new RuleReviewDialog(rootPane.getScene().getWindow(), ruleService, accountService,
                            categoryService, labelService,
                            onlyFillMissing -> ruleService.previewRulesForTransactions(
                                    newTransactionIds, onlyFillMissing), false).show();
                }
            });
            return;
        }
        report.showAndWait();
    }

    private String accountName(Long accountId) {
        return accountService.getAccount(accountId)
                .map(account -> {
                    String name = account.getBankName() == null ? "" : account.getBankName();
                    String iban = maskIban(account.getIban());
                    String label = iban.isBlank() ? name : name + " (" + iban + ")";
                    return label.isBlank() ? String.valueOf(accountId) : label;
                })
                .orElse(String.valueOf(accountId));
    }

    private String errorMessage(Exception exception) {
        return exception.getMessage() == null ? exception.toString() : exception.getMessage();
    }

    @FXML
    private void onClearSyncLog() {
        syncLogContainer.getChildren().clear();
        Label noLogs = new Label(I18n.get("message.noSyncLogs"));
        noLogs.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");
        syncLogContainer.getChildren().add(noLogs);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        if (rootPane.getScene() != null) {
            alert.getDialogPane().getStylesheets().addAll(rootPane.getScene().getStylesheets());
        }
        alert.showAndWait();
    }

}
