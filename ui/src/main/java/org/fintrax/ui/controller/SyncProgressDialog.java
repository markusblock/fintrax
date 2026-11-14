package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.fintrax.config.I18n;
import org.fintrax.model.SyncStage;
import org.fintrax.service.SyncProgress;
import org.fintrax.service.SyncResult;
import org.fintrax.ui.FintraxUI;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class SyncProgressDialog {
    private static final String STATE_PENDING = "pending";
    private static final String STATE_CURRENT = "current";
    private static final String STATE_COMPLETE = "complete";
    private static final String STATE_FAILED = "failed";
    private static final String ICON_PENDING = "fth-circle";
    private static final String ICON_COMPLETE = "fth-check-circle";
    private static final String ICON_FAILED = "fth-alert-circle";
    private static final List<SyncStage> PROGRESS_STAGES = List.of(
            SyncStage.CONNECTION, SyncStage.AUTHENTICATION, SyncStage.DATA_RETRIEVAL, SyncStage.DATA_IMPORT);

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final Label summaryLabel = new Label();
    private final ProgressBar accountProgress = new ProgressBar(0);
    private final VBox accountList = new VBox(8);
    private final Map<Long, AccountPanel> accountPanels = new LinkedHashMap<>();
    private final ButtonType activityLogButton = new ButtonType(
            I18n.get("button.viewActivityLog"), ButtonBar.ButtonData.HELP);
    private final ButtonType applyRulesButton = new ButtonType(
            I18n.get("button.applyRulesToNewTransactions"), ButtonBar.ButtonData.OK_DONE);
    private final ButtonType closeButton = new ButtonType(
            I18n.get("button.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
    private int totalAccounts;
    private boolean showingReport;
    private Runnable cancelAction = () -> { };

    SyncProgressDialog(Window owner) {
        dialog.setTitle(I18n.get("dialog.syncProgress.title"));
        dialog.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        summaryLabel.setWrapText(true);
        summaryLabel.setId("syncProgressOverview");
        summaryLabel.getStyleClass().add("sync-progress-account");
        accountProgress.setMaxWidth(Double.MAX_VALUE);
        accountList.getStyleClass().add("sync-progress-accounts");
        ScrollPane scrollPane = new ScrollPane(accountList);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        VBox content = new VBox(12, summaryLabel, accountProgress, scrollPane);
        content.getStyleClass().add("sync-progress-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(activityLogButton, applyRulesButton, closeButton);
        dialog.getDialogPane().setPrefSize(650, 600);
        dialog.setResizable(false);
        Node activityLog = dialog.getDialogPane().lookupButton(activityLogButton);
        Node applyRules = dialog.getDialogPane().lookupButton(applyRulesButton);
        Node close = dialog.getDialogPane().lookupButton(closeButton);
        activityLog.setId("viewSyncActivityLogButton");
        activityLog.setDisable(true);
        applyRules.setId("applyRulesToNewTransactions");
        applyRules.setDisable(true);
        close.setId("syncReportCloseButton");
        close.addEventFilter(ActionEvent.ACTION, event -> {
            if (!showingReport) {
                event.consume();
                cancelAction.run();
                close();
            }
        });
        dialog.setOnCloseRequest(event -> {
            if (!showingReport) {
                event.consume();
            }
        });
        FintraxUI.configureDialog(dialog);
    }

    void show(List<Account> accounts) {
        accountPanels.clear();
        accountList.getChildren().clear();
        totalAccounts = accounts.size();
        for (Account account : accounts) {
            AccountPanel panel = new AccountPanel(account);
            accountPanels.put(account.id(), panel);
            accountList.getChildren().add(panel.root());
        }
        updateTotals();
        dialog.show();
        if (!accounts.isEmpty()) {
            activateAccount(accounts.getFirst().id());
        }
    }

    void activateAccount(Long accountId) {
        runOnFxThread(() -> {
            if (!dialog.isShowing() || showingReport) {
                return;
            }
            AccountPanel panel = accountPanels.get(accountId);
            if (panel == null) {
                return;
            }
            panel.activate();
        });
    }

    void updateProgress(SyncProgress progress) {
        runOnFxThread(() -> {
            if (!dialog.isShowing() || showingReport) {
                return;
            }
            AccountPanel panel = accountPanels.get(progress.accountId());
            if (panel != null) {
                panel.update(progress);
                updateTotals();
            }
        });
    }

    void finishAccount(SyncResult result) {
        runOnFxThread(() -> {
            if (!dialog.isShowing() || showingReport) {
                return;
            }
            Long accountId = result.syncLog().getBankAccountId();
            AccountPanel panel = accountPanels.get(accountId);
            if (panel != null) {
                panel.finish(result);
                updateTotals();
            }
        });
    }

    void failAccount(Long accountId, String message) {
        runOnFxThread(() -> {
            if (!dialog.isShowing() || showingReport) {
                return;
            }
            AccountPanel panel = accountPanels.get(accountId);
            if (panel != null) {
                panel.fail(message, null);
                updateTotals();
            }
        });
    }

    void showReport(String title, List<ButtonType> buttons, Consumer<ButtonType> onClose) {
        showingReport = true;
        dialog.setTitle(title);
        dialog.getDialogPane().setHeaderText(title);
        updateTotals();
        accountProgress.setProgress(1);
        dialog.getDialogPane().getButtonTypes().setAll(buttons);
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(760, 600);
        dialog.setResultConverter(button -> button);
        dialog.setOnHidden(event -> onClose.accept(dialog.getResult()));
        if (!dialog.isShowing()) {
            dialog.show();
        }
    }

    void setButtonId(ButtonType buttonType, String id) {
        Node button = dialog.getDialogPane().lookupButton(buttonType);
        button.setId(id);
    }

    void setButtonDisabled(ButtonType buttonType, boolean disabled) {
        Node button = dialog.getDialogPane().lookupButton(buttonType);
        button.setDisable(disabled);
    }

    void setCancelAction(Runnable cancelAction) {
        this.cancelAction = cancelAction;
    }

    void close() {
        showingReport = true;
        dialog.close();
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void updateTotals() {
        int finishedAccounts = 0;
        int successfulAccounts = 0;
        int failedAccounts = 0;
        for (AccountPanel panel : accountPanels.values()) {
            if (panel.isFinished()) {
                finishedAccounts++;
                if (panel.isFailed()) {
                    failedAccounts++;
                } else {
                    successfulAccounts++;
                }
            }
        }
        accountProgress.setProgress(totalAccounts == 0 ? 0 : (double) finishedAccounts / totalAccounts);
        summaryLabel.setText(I18n.get(totalAccounts == 1
                ? "message.sync.progress.overview.single" : "message.sync.progress.overview.multiple",
                totalAccounts, successfulAccounts, failedAccounts));
    }

    record Account(Long id, String name, String detail) {
    }

    private final class AccountPanel {
        private final VBox root = new VBox();
        private final Button toggle = new Button();
        private final FontIcon statusIcon = new FontIcon(ICON_PENDING);
        private final ProgressIndicator statusSpinner = new ProgressIndicator(
                ProgressIndicator.INDETERMINATE_PROGRESS);
        private final Label accountName = new Label();
        private final Label accountDetail = new Label();
        private final Label resultLabel = new Label();
        private final VBox stages = new VBox(7);
        private final Label errorLabel = new Label();
        private final Map<SyncStage, StageRow> stageRows = new LinkedHashMap<>();
        private boolean expanded;
        private boolean active;
        private boolean finished;
        private boolean failed;
        private int downloaded;
        private int imported;
        private int alreadyKnown;

        private AccountPanel(Account account) {
            root.getStyleClass().addAll("sync-progress-account-card", STATE_PENDING);

            statusIcon.setIconSize(18);
            statusIcon.getStyleClass().add("sync-progress-account-icon");
            statusSpinner.setPrefSize(18, 18);
            statusSpinner.setMaxSize(18, 18);
            statusSpinner.setVisible(false);
            statusSpinner.setManaged(false);
            StackPane status = new StackPane(statusIcon, statusSpinner);
            status.setMinWidth(26);

            accountName.setText(account.name());
            accountName.getStyleClass().add("sync-progress-account-name");
            accountDetail.setText(account.detail());
            accountDetail.getStyleClass().add("sync-progress-account-detail");
            VBox names = new VBox(2, accountName, accountDetail);
            HBox.setHgrow(names, Priority.ALWAYS);
            resultLabel.getStyleClass().add("sync-progress-account-result");
            Label chevron = new Label("⌄");
            chevron.getStyleClass().add("sync-progress-chevron");
            HBox header = new HBox(10, status, names, resultLabel, chevron);
            header.setAlignment(Pos.CENTER_LEFT);
            toggle.setGraphic(header);
            toggle.setMaxWidth(Double.MAX_VALUE);
            toggle.setId("syncProgressAccountToggle_" + account.id());
            toggle.setAccessibleText(account.name());
            toggle.getStyleClass().add("sync-progress-account-toggle");
            toggle.setOnAction(event -> setExpanded(!expanded));

            for (SyncStage stage : PROGRESS_STAGES) {
                StageRow row = new StageRow(account.id(), stage);
                stageRows.put(stage, row);
                stages.getChildren().add(row.root());
            }
            stages.getStyleClass().add("sync-progress-stage-list");
            errorLabel.setWrapText(true);
            errorLabel.getStyleClass().add("sync-progress-error");
            errorLabel.setId("syncProgressError_" + account.id());
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            VBox body = new VBox(8, stages, errorLabel);
            body.setId("syncProgressAccountBody_" + account.id());
            body.getStyleClass().add("sync-progress-account-body");
            body.setVisible(false);
            body.setManaged(false);
            root.getChildren().addAll(toggle, body);
            setHeaderState();
        }

        private Node root() {
            return root;
        }

        private void activate() {
            active = true;
            setExpanded(true);
            setHeaderState();
        }

        private void update(SyncProgress progress) {
            downloaded = Math.max(downloaded, progress.transactionsDownloaded());
            imported = Math.max(imported, progress.transactionsImported());
            alreadyKnown = Math.max(alreadyKnown, progress.transactionsAlreadyKnown());
            active = progress.stage() != SyncStage.COMPLETE;
            updateStages(progress.stage());
            setHeaderState();
        }

        private void finish(SyncResult result) {
            finished = true;
            active = false;
            failed = result.syncLog().getStatus() != org.fintrax.model.SyncStatus.SUCCESS;
            if (failed) {
                fail(result.syncLog().getErrorMessage(), result.syncLog().getStage());
            } else {
                downloaded = result.syncLog().getNewCount() + result.syncLog().getSkippedCount();
                imported = result.syncLog().getNewCount();
                alreadyKnown = result.syncLog().getSkippedCount();
                updateStages(SyncStage.COMPLETE);
                setExpanded(false);
                setHeaderState();
            }
        }

        private void fail(String message, SyncStage failedStage) {
            failed = true;
            finished = true;
            active = false;
            if (failedStage != null) {
                updateStages(failedStage);
                StageRow row = stageRows.get(failedStage);
                if (row != null) {
                    row.setFailed();
                }
            }
            errorLabel.setText(message == null || message.isBlank()
                    ? I18n.get("message.sync.report.noErrorDetails") : message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            setExpanded(true);
            setHeaderState();
        }

        private void updateStages(SyncStage currentStage) {
            int currentIndex = currentStage == SyncStage.COMPLETE
                    ? PROGRESS_STAGES.size() : PROGRESS_STAGES.indexOf(currentStage);
            for (int i = 0; i < PROGRESS_STAGES.size(); i++) {
                SyncStage stage = PROGRESS_STAGES.get(i);
                StageRow row = stageRows.get(stage);
                row.setState(stateFor(i, currentIndex));
                row.setResult(stage, downloaded, imported, alreadyKnown);
            }
        }

        private StageState stateFor(int index, int currentIndex) {
            if (index < currentIndex) return StageState.COMPLETE;
            if (index == currentIndex) return StageState.CURRENT;
            return StageState.PENDING;
        }

        private void setHeaderState() {
            String stateClass = accountStateClass();
            root.getStyleClass().removeAll(STATE_PENDING, STATE_CURRENT, STATE_COMPLETE, STATE_FAILED);
            root.getStyleClass().add(stateClass);
            statusIcon.setIconLiteral(accountStateIcon());
            statusSpinner.setVisible(active && !finished && !failed);
            statusSpinner.setManaged(active && !finished && !failed);
            statusIcon.setVisible(!active || finished || failed);
            resultLabel.setText(accountResult());
        }

        private String accountStateClass() {
            if (failed) return STATE_FAILED;
            if (finished) return STATE_COMPLETE;
            return active ? STATE_CURRENT : STATE_PENDING;
        }

        private String accountStateIcon() {
            if (failed) return ICON_FAILED;
            return finished ? ICON_COMPLETE : ICON_PENDING;
        }

        private String accountResult() {
            if (failed) return I18n.get("message.sync.progress.failed");
            if (finished) {
                return I18n.get("message.sync.progress.accountResult", downloaded, imported, alreadyKnown);
            }
            return I18n.get(active ? "message.sync.progress.running" : "message.sync.progress.notStarted");
        }

        private void setExpanded(boolean expanded) {
            this.expanded = expanded;
            Node body = root.getChildren().get(1);
            body.setVisible(expanded);
            body.setManaged(expanded);
            toggle.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("expanded"), expanded);
        }

        private boolean isFinished() {
            return finished;
        }

        private boolean isFailed() {
            return failed;
        }
    }

    private static final class StageRow {
        private final SyncStage stage;
        private final HBox root = new HBox(10);
        private final FontIcon icon = new FontIcon(ICON_PENDING);
        private final ProgressIndicator spinner = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
        private final Label stageLabel;
        private final Label resultLabel = new Label();

        private StageRow(Long accountId, SyncStage stage) {
            this.stage = stage;
            icon.setIconSize(16);
            icon.setId("syncProgressStatus_" + accountId + "_" + stage.name());
            icon.getStyleClass().addAll("sync-progress-stage-icon", STATE_PENDING);
            spinner.setPrefSize(16, 16);
            spinner.setMaxSize(16, 16);
            spinner.setId("syncProgressSpinner_" + accountId + "_" + stage.name());
            spinner.setVisible(false);
            spinner.setManaged(false);
            StackPane marker = new StackPane(icon, spinner);
            marker.setMinWidth(22);
            stageLabel = new Label(I18n.get("sync.progressStage." + stage.name()));
            stageLabel.setId("syncProgressStage_" + accountId + "_" + stage.name());
            stageLabel.getStyleClass().add("sync-progress-stage-label");
            HBox.setHgrow(stageLabel, Priority.ALWAYS);
            resultLabel.getStyleClass().add("sync-progress-stage-result");
            resultLabel.setId("syncProgressResult_" + accountId + "_" + stage.name());
            resultLabel.setWrapText(true);
            root.getChildren().addAll(marker, stageLabel, resultLabel);
            root.setAlignment(Pos.CENTER_LEFT);
            root.getStyleClass().add("sync-progress-stage-row");
        }

        private Node root() {
            return root;
        }

        private void setState(StageState state) {
            String stateClass = stateClass(state);
            root.getStyleClass().removeAll(STATE_PENDING, STATE_CURRENT, STATE_COMPLETE, STATE_FAILED);
            root.getStyleClass().add(stateClass);
            icon.getStyleClass().removeAll(STATE_PENDING, STATE_CURRENT, STATE_COMPLETE, STATE_FAILED);
            icon.getStyleClass().add(stateClass);
            stageLabel.getStyleClass().removeAll(STATE_PENDING, STATE_CURRENT, STATE_COMPLETE, STATE_FAILED);
            stageLabel.getStyleClass().add(stateClass);
            icon.setIconLiteral(state == StageState.COMPLETE ? ICON_COMPLETE : ICON_PENDING);
            icon.setVisible(state != StageState.CURRENT);
            spinner.setVisible(state == StageState.CURRENT);
            spinner.setManaged(state == StageState.CURRENT);
        }

        private void setFailed() {
            root.getStyleClass().removeAll(STATE_PENDING, STATE_CURRENT, STATE_COMPLETE, STATE_FAILED);
            root.getStyleClass().add(STATE_FAILED);
            icon.getStyleClass().removeAll(STATE_PENDING, STATE_CURRENT, STATE_COMPLETE, STATE_FAILED);
            icon.getStyleClass().add(STATE_FAILED);
            icon.setIconLiteral(ICON_FAILED);
            icon.setVisible(true);
            spinner.setVisible(false);
            spinner.setManaged(false);
            stageLabel.getStyleClass().removeAll(STATE_PENDING, STATE_CURRENT, STATE_COMPLETE);
            stageLabel.getStyleClass().add(STATE_FAILED);
        }

        private String stateClass(StageState state) {
            return switch (state) {
                case PENDING -> STATE_PENDING;
                case CURRENT -> STATE_CURRENT;
                case COMPLETE -> STATE_COMPLETE;
            };
        }

        private void setResult(SyncStage updatedStage, int downloaded, int imported, int alreadyKnown) {
            if (stage == SyncStage.DATA_RETRIEVAL && (downloaded > 0 || updatedStage == SyncStage.COMPLETE)) {
                resultLabel.setText(I18n.get("message.sync.progress.downloaded", downloaded));
            } else if (stage == SyncStage.DATA_IMPORT
                    && (updatedStage == SyncStage.DATA_IMPORT || updatedStage == SyncStage.COMPLETE)) {
                resultLabel.setText(I18n.get("message.sync.progress.imported", imported, downloaded, alreadyKnown));
            } else {
                resultLabel.setText("");
            }
        }
    }

    private enum StageState {
        PENDING,
        CURRENT,
        COMPLETE
    }
}
