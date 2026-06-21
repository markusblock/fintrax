package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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
import org.fintrax.model.BankAccount;
import org.fintrax.model.SyncLog;
import org.fintrax.model.SyncStatus;
import org.fintrax.service.AccountService;
import org.fintrax.service.ServiceRegistry;
import org.fintrax.service.SyncService;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
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

    private final AccountService accountService = ServiceRegistry.getInstance().getAccountService();
    private final SyncService syncService = ServiceRegistry.getInstance().getSyncService();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sync-worker");
        t.setDaemon(true);
        return t;
    });

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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/addAccountDialog.fxml"));
            loader.setResources(I18n.getResourceBundle());
            Parent root = loader.load();

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
        for (BankAccount account : accounts) {
            onSyncAccount(account.getId());
        }
    }

    private void onSyncAccount(Long accountId) {
        String pin = ServiceRegistry.getInstance().getPinStorage().retrievePin(String.valueOf(accountId));

        if (pin == null) {
            pin = showPinDialog(accountId);
            if (pin == null) return;
        }

        final String finalPin = pin;
        executor.submit(() -> {
            try {
                SyncLog syncLog = syncService.syncAccount(accountId, finalPin);
                Platform.runLater(() -> {
                    loadAccounts();
                    loadSyncLogs();
                    showSyncResult(syncLog);
                });
            } catch (Exception e) {
                log.error("Sync failed for account {}", accountId, e);
                Platform.runLater(() -> {
                    loadSyncLogs();
                    showError(I18n.get("message.sync.failed", e.getMessage()));
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
            ServiceRegistry.getInstance().getPinStorage().deletePin(String.valueOf(account.getId()));
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

    private void showSyncResult(SyncLog syncLog) {
        if (syncLog.getStatus() == SyncStatus.SUCCESS) {
            showInfo(I18n.get("message.sync.success", syncLog.getNewCount(), syncLog.getSkippedCount()));
        } else if (syncLog.getStatus() == SyncStatus.FAILED) {
            showError(I18n.get("message.sync.failed", syncLog.getErrorMessage()));
        }
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

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setContentText(message);
        if (rootPane.getScene() != null) {
            alert.getDialogPane().getStylesheets().addAll(rootPane.getScene().getStylesheets());
        }
        alert.showAndWait();
    }
}
