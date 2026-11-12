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
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.fintx.PinStorage;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;
import org.fintrax.model.SyncLog;
import org.fintrax.model.SyncStage;
import org.fintrax.model.SyncStatus;
import org.fintrax.service.AccountService;
import org.fintrax.service.BankService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.RuleService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.SyncResult;
import org.fintrax.service.SyncService;
import org.fintrax.ui.FintraxUI;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@Scope("prototype")
public class BankAccountsController {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String ACCOUNT_ACTION_BUTTON_STYLE_CLASS = "account-action-button";
    private static final List<SyncStage> DISPLAYED_SYNC_STAGES = List.of(
            SyncStage.CONNECTION, SyncStage.AUTHENTICATION, SyncStage.DATA_RETRIEVAL, SyncStage.DATA_IMPORT);

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
    private final BankService bankService;
    private final SyncService syncService;
    private final RuleService ruleService;
    private final SettingsService settingsService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final PinStorage pinStorage;
    private final ViewLoader viewLoader;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sync-worker");
        t.setDaemon(true);
        return t;
    });

    public BankAccountsController(AccountService accountService, BankService bankService, SyncService syncService,
                                  PinStorage pinStorage, ViewLoader viewLoader, RuleService ruleService,
                                  CategoryService categoryService, LabelService labelService,
                                  SettingsService settingsService) {
        this.accountService = accountService;
        this.bankService = bankService;
        this.syncService = syncService;
        this.pinStorage = pinStorage;
        this.viewLoader = viewLoader;
        this.ruleService = ruleService;
        this.settingsService = settingsService;
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
            emptyLabel.getStyleClass().add("empty-state");
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
        card.setPrefWidth(300);
        card.setPadding(new Insets(15));

        HBox header = new HBox(8);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Bank bank = bankService.getBankForAccount(account).orElse(null);
        String bankName = bank == null ? null : bank.getName();
        if (bankName == null || bankName.isBlank()) {
            bankName = account.getBankName();
        }
        Label bankNameLabel = new Label(bankName != null ? bankName : "Unknown Bank");
        bankNameLabel.getStyleClass().add("account-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label typeLabel = new Label(account.getAccountType() != null ? account.getAccountType().name() : "");
        typeLabel.getStyleClass().add("account-type");

        header.getChildren().addAll(bankNameLabel, spacer, typeLabel);

        String accountName = account.getAccountName() == null ? account.getBankName() : account.getAccountName();
        Label accountNameLabel = new Label(accountName == null ? "" : accountName);
        accountNameLabel.getStyleClass().add("account-holder");

        Label holderLabel = new Label(account.getAccountHolder() != null ? account.getAccountHolder() : "");
        holderLabel.getStyleClass().add("account-holder");

        String maskedIban = maskIban(account.getIban());
        Label ibanLabel = new Label(maskedIban);
        ibanLabel.getStyleClass().add("account-iban");

        Label balanceLabel = new Label(formatBalance(account.getBalance()));
        balanceLabel.getStyleClass().add("account-balance");
        balanceLabel.getStyleClass().add(account.getBalance() != null && account.getBalance().signum() < 0
                ? "negative" : "positive");

        String lastSyncText = account.getLastSyncAt() != null
                ? I18n.get("label.lastSync") + ": " + account.getLastSyncAt().format(DATE_FMT)
                : I18n.get("label.neverSynced");
        Label lastSyncLabel = new Label(lastSyncText);
        lastSyncLabel.getStyleClass().add("account-last-sync");

        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Button syncButton = new Button();
        syncButton.getStyleClass().addAll("sync-button", ACCOUNT_ACTION_BUTTON_STYLE_CLASS);
        syncButton.setGraphic(icon("fth-refresh-cw", 14));
        syncButton.setTooltip(new Tooltip(I18n.get("button.sync")));
        syncButton.setAccessibleText(I18n.get("button.sync"));
        syncButton.setOnAction(e -> onSyncAccount(account.getId()));

        Button editButton = new Button();
        editButton.getStyleClass().addAll("button-outlined", ACCOUNT_ACTION_BUTTON_STYLE_CLASS);
        editButton.setId("editAccountButton-" + account.getId());
        editButton.setGraphic(icon("fth-edit-2", 14));
        editButton.setTooltip(new Tooltip(I18n.get("button.edit")));
        editButton.setAccessibleText(I18n.get("button.edit"));
        editButton.setOnAction(e -> onEditAccount(account));

        Button deleteButton = new Button();
        deleteButton.getStyleClass().addAll("delete-button", ACCOUNT_ACTION_BUTTON_STYLE_CLASS);
        deleteButton.setGraphic(icon("fth-trash-2", 14));
        deleteButton.setTooltip(new Tooltip(I18n.get("button.delete")));
        deleteButton.setAccessibleText(I18n.get("button.delete"));
        deleteButton.setOnAction(e -> onDeleteAccount(account));

        buttonBox.getChildren().addAll(syncButton, editButton, deleteButton);

        card.getChildren().addAll(header, accountNameLabel, holderLabel, ibanLabel, balanceLabel, lastSyncLabel,
                buttonBox);
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
            Window ownerWindow = addAccountButton.getScene().getWindow();
            dialog.initOwner(ownerWindow);
            Scene dialogScene = new Scene(root);
            FintraxUI.configureScene(dialogScene);
            dialog.setScene(dialogScene);
            dialog.setMinWidth(560);
            dialog.sizeToScene();

            dialog.showAndWait();
            loadAccounts();
        } catch (Exception e) {
            log.error("Failed to open add account dialog", e);
            showError(I18n.get("error.dialog.open", e.getMessage()));
        }
    }

    private void onEditAccount(BankAccount account) {
        try {
            Parent root = viewLoader.load("editAccountDialog");
            EditAccountDialogController editController = (EditAccountDialogController) root.getProperties()
                    .get(EditAccountDialogController.class.getName());
            editController.setAccount(account);

            Stage dialog = new Stage();
            dialog.setTitle(I18n.get("dialog.editAccount.title"));
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(rootPane.getScene().getWindow());
            Scene dialogScene = new Scene(root, 560, 680);
            FintraxUI.configureScene(dialogScene);
            dialog.setScene(dialogScene);

            dialog.showAndWait();
            loadAccounts();
        } catch (Exception e) {
            log.error("Failed to open edit account dialog", e);
            showError(I18n.get("error.dialog.open", e.getMessage()));
        }
    }

    @FXML
    private void onSyncAll() {
        List<BankAccount> accounts = accountService.getAllAccounts();
        Map<String, String> passwordsByBank = new LinkedHashMap<>();
        Map<Long, String> bankKeyByAccount = new LinkedHashMap<>();
        Set<String> promptedBanks = new HashSet<>();
        for (BankAccount account : accounts) {
            String bankKey = syncBankKey(account);
            bankKeyByAccount.put(account.getId(), bankKey);
            if (promptedBanks.add(bankKey)) {
                long accountCount = accounts.stream()
                        .filter(candidate -> bankKey.equals(syncBankKey(candidate)))
                        .count();
                String password = showPinDialog(bankDisplayName(account), (int) accountCount);
                if (password != null) {
                    passwordsByBank.put(bankKey, password);
                }
            }
        }
        if (passwordsByBank.isEmpty()) return;

        executor.submit(() -> {
            List<SyncResult> results = new ArrayList<>();
            Map<Long, String> failures = new LinkedHashMap<>();
            accounts.stream()
                    .filter(account -> passwordsByBank.containsKey(bankKeyByAccount.get(account.getId())))
                    .forEach(account -> {
                try {
                    results.add(syncService.syncAccount(account.getId(),
                            passwordsByBank.get(bankKeyByAccount.get(account.getId()))));
                } catch (Exception e) {
                    log.warn("Bank sync failed for account {}", account.getId());
                    failures.put(account.getId(), I18n.get("message.sync.failedSafe"));
                }
            });
            passwordsByBank.clear();
            Platform.runLater(() -> {
                loadAccounts();
                loadSyncLogs();
                showSyncReport(results, failures);
            });
        });
    }

    private void onSyncAccount(Long accountId) {
        Optional<BankAccount> account = accountService.getAccount(accountId);
        if (account.isEmpty()) return;
        String pin = showPinDialog(bankDisplayName(account.get()), 1);
        if (pin == null) return;

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
                log.warn("Bank sync failed for account {}", accountId);
                Platform.runLater(() -> {
                    loadSyncLogs();
                    showSyncReport(List.of(), Map.of(accountId,
                            I18n.get("message.sync.failedSafe")));
                });
            }
        });
    }

    private String showPinDialog(String bankName, int accountCount) {
        return PinPromptDialog.show(rootPane.getScene().getWindow(), bankName, accountCount, I18n.get("button.sync"));
    }

    private String syncBankKey(BankAccount account) {
        return account.getBankId() == null
                ? "account:" + account.getId() : "bank:" + account.getBankId();
    }

    private String bankDisplayName(BankAccount account) {
        return bankService.getBankForAccount(account)
                .map(Bank::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(account.getBankName() == null ? account.getAccountName() : account.getBankName());
    }

    private String accountDisplayName(BankAccount account) {
        String bankName = bankDisplayName(account);
        String accountName = account.getAccountName();
        if (accountName == null || accountName.isBlank() || accountName.equals(bankName)) {
            return bankName == null ? "" : bankName;
        }
        return bankName == null || bankName.isBlank() ? accountName : bankName + " · " + accountName;
    }

    private void onDeleteAccount(BankAccount account) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.get("dialog.deleteAccount.title"));
        alert.setHeaderText(I18n.get("dialog.deleteAccount.header"));
        alert.setContentText(I18n.get("dialog.deleteAccount.content", accountDisplayName(account), account.getIban()));
        FintraxUI.configureDialog(alert);

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
            noLogs.getStyleClass().addAll("empty-state", "compact");
            syncLogContainer.getChildren().add(noLogs);
        }
    }

    private HBox createSyncLogRow(BankAccount account, SyncLog syncLog) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 5, 3, 5));

        String statusIcon = switch (syncLog.getStatus()) {
            case SUCCESS -> "fth-check-circle";
            case FAILED -> "fth-alert-circle";
            case PARTIAL -> "fth-alert-triangle";
        };

        Label statusLabel = new Label();
        statusLabel.setGraphic(icon(statusIcon, 16));
        statusLabel.getStyleClass().add(switch (syncLog.getStatus()) {
            case SUCCESS -> "status-success";
            case FAILED -> "status-error";
            case PARTIAL -> "status-warning";
        });
        String accountDisplayName = accountDisplayName(account);
        Label accountLabel = new Label(accountDisplayName.isBlank() ? account.getIban() : accountDisplayName);
        accountLabel.getStyleClass().add("sync-account-name");

        String message;
        if (syncLog.getStatus() == SyncStatus.FAILED) {
            String error = syncLog.getErrorMessage() != null
                    ? syncLog.getErrorMessage() : I18n.get("message.sync.report.noErrorDetails");
            message = syncLog.getStage() == null
                    ? I18n.get("message.sync.failed", error)
                    : I18n.get("message.sync.failedAtStage",
                            I18n.get("sync.failureStage." + syncLog.getStage().name()), error);
        } else {
            message = I18n.get("message.sync.success", syncLog.getNewCount(), syncLog.getSkippedCount());
        }
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("sync-message");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String timeText = syncLog.getCompletedAt() != null ? syncLog.getCompletedAt().format(DATE_FMT) : "";
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("sync-time");

        row.getChildren().addAll(statusLabel, accountLabel, messageLabel, spacer, timeLabel);

        row.getStyleClass().add("sync-log-row");
        if (syncLog.getStatus() == SyncStatus.FAILED) {
            row.getStyleClass().add("failed");
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

        List<Map.Entry<Long, String>> additionalFailures = failures.entrySet().stream()
                .filter(failure -> results.stream().noneMatch(result ->
                        java.util.Objects.equals(result.syncLog().getBankAccountId(), failure.getKey())))
                .toList();
        int successfulAccounts = (int) results.stream()
                .filter(result -> result.syncLog().getStatus() == SyncStatus.SUCCESS).count();
        int failedAccounts = results.size() - successfulAccounts + additionalFailures.size();
        int accountCount = results.size() + additionalFailures.size();
        boolean hasFailures = failedAccounts > 0;
        Alert report = new Alert(hasFailures ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION);
        report.setTitle(I18n.get("message.sync.report.title"));
        report.setHeaderText(I18n.get("message.sync.report.title"));
        report.setContentText(null);
        report.getDialogPane().setContent(createSyncReportContent(results, additionalFailures,
                accountCount, successfulAccounts, failedAccounts, newCount, skippedCount));
        report.getDialogPane().setPrefSize(760, 560);
        report.setResizable(true);
        report.initOwner(rootPane.getScene().getWindow());
        FintraxUI.configureDialog(report);

        ButtonType closeType = new ButtonType(I18n.get("button.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType viewActivityLogType = new ButtonType(
                I18n.get("button.viewActivityLog"), ButtonBar.ButtonData.HELP);
        ButtonType applyRulesType = new ButtonType(
                I18n.get("button.applyRulesToNewTransactions"), ButtonBar.ButtonData.OK_DONE);
        List<ButtonType> buttons = new ArrayList<>();
        if (hasFailures) {
            buttons.add(viewActivityLogType);
        }
        if (!newTransactionIds.isEmpty()) {
            buttons.add(applyRulesType);
        }
        buttons.add(closeType);
        report.getButtonTypes().setAll(buttons);
        if (buttons.contains(applyRulesType)) {
            ((Button) report.getDialogPane().lookupButton(applyRulesType)).setId("applyRulesToNewTransactions");
        }
        ((Button) report.getDialogPane().lookupButton(closeType)).setId("syncReportCloseButton");
        if (buttons.contains(viewActivityLogType)) {
            ((Button) report.getDialogPane().lookupButton(viewActivityLogType)).setId("viewSyncActivityLogButton");
        }

        report.showAndWait().ifPresent(button -> {
            if (button == viewActivityLogType) {
                openActivityLog();
            } else if (button == applyRulesType) {
                new RuleReviewDialog(rootPane.getScene().getWindow(),
                        new RuleReviewDialog.Services(ruleService, accountService, categoryService, labelService,
                                settingsService),
                        onlyFillMissing -> ruleService.previewRulesForTransactions(
                                newTransactionIds, onlyFillMissing), false).show();
            }
        });
    }

    private VBox createSyncReportContent(List<SyncResult> results,
                                         List<Map.Entry<Long, String>> additionalFailures,
                                         int accountCount, int successfulAccounts, int failedAccounts,
                                         int newCount, int skippedCount) {
        VBox content = new VBox(12);
        content.getStyleClass().add("sync-report-content");
        Label summary = new Label(I18n.get("message.sync.report.batchSummary", accountCount,
                successfulAccounts, failedAccounts, newCount, skippedCount));
        summary.getStyleClass().add("section-heading");

        VBox accountResults = new VBox(8);
        for (SyncResult result : results) {
            accountResults.getChildren().add(createAccountSyncResult(result.syncLog(), accountCount));
        }
        for (Map.Entry<Long, String> failure : additionalFailures) {
            SyncLog failedLog = SyncLog.builder().bankAccountId(failure.getKey())
                    .status(SyncStatus.FAILED).errorMessage(failure.getValue()).build();
            accountResults.getChildren().add(createAccountSyncResult(failedLog, accountCount));
        }

        ScrollPane scrollPane = new ScrollPane(accountResults);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        content.getChildren().addAll(summary, scrollPane);
        return content;
    }

    private TitledPane createAccountSyncResult(SyncLog syncLog, int resultCount) {
        boolean succeeded = syncLog.getStatus() == SyncStatus.SUCCESS;
        SyncStage failureStage = syncLog.getStage();
        String header;
        if (succeeded) {
            header = I18n.get("message.sync.report.accountHeader.success", accountName(syncLog.getBankAccountId()),
                    syncLog.getNewCount(), syncLog.getSkippedCount());
        } else if (failureStage == null) {
            header = I18n.get("message.sync.report.accountHeader.unknown", accountName(syncLog.getBankAccountId()));
        } else {
            header = I18n.get("message.sync.report.accountHeader.failure", accountName(syncLog.getBankAccountId()),
                    I18n.get("sync.failureStage." + failureStage.name()));
        }

        VBox details = new VBox(8);
        details.getStyleClass().add("sync-report-account-content");
        int failedStageIndex = failureStage == null ? -1 : DISPLAYED_SYNC_STAGES.indexOf(failureStage);
        for (int i = 0; i < DISPLAYED_SYNC_STAGES.size(); i++) {
            SyncStage stage = DISPLAYED_SYNC_STAGES.get(i);
            String statusKey;
            String stateClass;
            String marker;
            if (succeeded || (!succeeded && failedStageIndex >= 0 && i < failedStageIndex)) {
                statusKey = "message.sync.report.stage.done";
                stateClass = "complete";
                marker = "✓";
            } else if (!succeeded && i == failedStageIndex) {
                statusKey = "message.sync.report.stage.failed";
                stateClass = "failed";
                marker = "!";
            } else if (failureStage == null) {
                statusKey = "message.sync.report.stage.unknown";
                stateClass = "unknown";
                marker = "?";
            } else {
                statusKey = "message.sync.report.stage.notStarted";
                stateClass = "pending";
                marker = "–";
            }

            HBox row = new HBox(10);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label statusMarker = new Label(marker);
            statusMarker.getStyleClass().addAll("sync-stage-marker", stateClass);
            String stageLabelKey = "failed".equals(stateClass) ? "sync.failureStage." : "sync.stage.";
            Label stageLabel = new Label(I18n.get(stageLabelKey + stage.name()));
            stageLabel.getStyleClass().addAll("sync-stage-label", stateClass);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label stateLabel = new Label(I18n.get(statusKey));
            stateLabel.getStyleClass().addAll("sync-stage-state", stateClass);
            row.getChildren().addAll(statusMarker, stageLabel, spacer, stateLabel);
            details.getChildren().add(row);
        }

        if (!succeeded) {
            if (failureStage == null) {
                Label unavailable = new Label(I18n.get("message.sync.report.stageUnavailable"));
                unavailable.setWrapText(true);
                unavailable.getStyleClass().add("warning-copy");
                details.getChildren().add(unavailable);
            } else if (failureStage != SyncStage.DATA_IMPORT) {
                Label notImported = new Label(I18n.get("message.sync.noDataImported"));
                notImported.setWrapText(true);
                notImported.getStyleClass().add("secondary-text");
                details.getChildren().add(notImported);
            }
            Label error = new Label(syncLog.getErrorMessage() == null || syncLog.getErrorMessage().isBlank()
                    ? I18n.get("message.sync.report.noErrorDetails") : syncLog.getErrorMessage());
            error.setWrapText(true);
            error.getStyleClass().add("sync-report-error");
            details.getChildren().add(error);
        }

        TitledPane pane = new TitledPane(header, details);
        pane.getStyleClass().add("sync-report-account");
        pane.setExpanded(!succeeded || resultCount == 1);
        return pane;
    }

    private void openActivityLog() {
        if (rootPane.getScene() == null) {
            return;
        }
        Object controller = rootPane.getScene().getRoot().getProperties().get(MainController.class.getName());
        if (controller instanceof MainController mainController) {
            mainController.showActivityLog();
        }
    }

    private String accountName(Long accountId) {
        return accountService.getAccount(accountId)
                .map(account -> {
                    String name = accountDisplayName(account);
                    String iban = maskIban(account.getIban());
                    String label = iban.isBlank() ? name : name + " (" + iban + ")";
                    return label.isBlank() ? String.valueOf(accountId) : label;
                })
                .orElse(String.valueOf(accountId));
    }

    @FXML
    private void onClearSyncLog() {
        syncLogContainer.getChildren().clear();
        Label noLogs = new Label(I18n.get("message.noSyncLogs"));
        noLogs.getStyleClass().addAll("empty-state", "compact");
        syncLogContainer.getChildren().add(noLogs);
    }

    private FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(literal);
        icon.setIconSize(size);
        return icon;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        FintraxUI.configureDialog(alert);
        alert.showAndWait();
    }

}
