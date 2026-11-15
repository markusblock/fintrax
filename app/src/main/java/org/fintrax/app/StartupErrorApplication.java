package org.fintrax.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.service.BackupRestoreService;
import org.fintrax.service.BackupRestoreService.RestoreResult;
import org.fintrax.store.BackupConfiguration;
import org.fintrax.ui.FintraxUI;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
public final class StartupErrorApplication extends Application {
    private static Path storageInUsePath;
    private static RecoveryRequest recoveryRequest;
    private static boolean restoredFromBackup;

    private ConfigurableApplicationContext restoredContext;

    public static void showStorageInUse(Path storagePath) {
        storageInUsePath = storagePath;
        recoveryRequest = null;
        Application.launch(StartupErrorApplication.class);
    }

    public static boolean showStorageFailure(Path storagePath, BackupConfiguration backupConfiguration,
                                             Throwable failure, String... args) {
        storageInUsePath = null;
        recoveryRequest = new RecoveryRequest(storagePath, backupConfiguration, failure, args.clone());
        restoredFromBackup = false;
        Application.launch(StartupErrorApplication.class);
        recoveryRequest = null;
        return restoredFromBackup;
    }

    @Override
    public void start(Stage primaryStage) {
        Locale startupLocale = Locale.GERMAN.getLanguage().equals(Locale.getDefault().getLanguage())
                ? Locale.GERMAN : Locale.ENGLISH;
        I18n.setLocale(startupLocale);
        if (storageInUsePath != null) {
            showStorageInUse(primaryStage, storageInUsePath);
            return;
        }
        showStorageRecovery(primaryStage, recoveryRequest);
    }

    private void showStorageInUse(Stage owner, Path storagePath) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        FintraxUI.configureDialog(alert);
        alert.initOwner(owner);
        alert.setTitle(I18n.get("app.name"));
        alert.setHeaderText(I18n.get("startup.storageInUse.title"));
        alert.setContentText(I18n.get("startup.storageInUse.message", storagePath));
        alert.showAndWait();
        Platform.exit();
    }

    private void showStorageRecovery(Stage owner, RecoveryRequest request) {
        BackupRestoreService restoreService = new BackupRestoreService();
        while (true) {
            List<Path> backups = listFullBackups(restoreService, request.backupConfiguration());

            Alert alert = new Alert(Alert.AlertType.ERROR);
            FintraxUI.configureDialog(alert);
            alert.initOwner(owner);
            alert.setTitle(I18n.get("startup.storageError.title"));
            alert.setHeaderText(I18n.get("startup.storageError.title"));
            String message = I18n.get("startup.storageError.message", request.storagePath(),
                    messageOf(request.failure()));
            if (backups.isEmpty()) {
                message += "\n" + I18n.get("startup.storageError.noBackups");
            }
            alert.setContentText(message);

            ButtonType exit = new ButtonType(I18n.get("startup.storageError.exit"));
            ButtonType restore = new ButtonType(I18n.get("startup.storageError.restore"));
            alert.getButtonTypes().setAll(backups.isEmpty() ? List.of(exit) : List.of(restore, exit));
            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isEmpty() || choice.get() == exit || backups.isEmpty()) {
                Platform.exit();
                return;
            }

            ChoiceDialog<Path> backupChoice = new ChoiceDialog<>(backups.getFirst(), backups);
            FintraxUI.configureDialog(backupChoice);
            backupChoice.initOwner(owner);
            backupChoice.setTitle(I18n.get("startup.storageError.choose.title"));
            backupChoice.setHeaderText(I18n.get("startup.storageError.choose.header"));
            Optional<Path> selectedBackup = backupChoice.showAndWait();
            if (selectedBackup.isEmpty()) {
                continue;
            }
            startRestore(owner, request, selectedBackup.get(), restoreService);
            return;
        }
    }

    private List<Path> listFullBackups(BackupRestoreService restoreService,
                                       BackupConfiguration backupConfiguration) {
        try {
            return restoreService.listFullBackups(backupConfiguration.snapshotsDirectory());
        } catch (Exception exception) {
            log.warn("Cannot list full backups", exception);
            return List.of();
        }
    }

    private void startRestore(Stage owner, RecoveryRequest request, Path selectedBackup,
                              BackupRestoreService restoreService) {
        Dialog<Void> progress = new Dialog<>();
        FintraxUI.configureDialog(progress);
        progress.initOwner(owner);
        progress.setTitle(I18n.get("startup.storageError.restoring.title"));
        progress.setHeaderText(I18n.get("startup.storageError.restoring.title"));
        progress.getDialogPane().setContent(new VBox(12,
                new ProgressIndicator(), new Label(I18n.get("startup.storageError.restoring.message"))));
        ButtonType restoring = new ButtonType(I18n.get("startup.storageError.restoring.button"));
        progress.getDialogPane().getButtonTypes().setAll(restoring);
        ((Button) progress.getDialogPane().lookupButton(restoring)).setDisable(true);
        progress.setOnCloseRequest(event -> event.consume());
        progress.show();

        Task<RestoreResult> restoreTask = new Task<>() {
            @Override
            protected RestoreResult call() throws Exception {
                return restoreService.restoreFullBackup(selectedBackup,
                        request.backupConfiguration().snapshotsDirectory(), request.storagePath(),
                        request.backupConfiguration().continuousBackupDirectory());
            }
        };
        restoreTask.setOnSucceeded(event -> {
            progress.close();
            completeRestore(owner, request, restoreTask.getValue());
        });
        restoreTask.setOnFailed(event -> {
            progress.close();
            Throwable failure = restoreTask.getException();
            log.error("Failed to restore full backup {}", selectedBackup, failure);
            showStorageRecovery(owner, request.withFailure(failure));
        });
        Thread worker = new Thread(restoreTask, "fintrax-backup-restore");
        worker.setDaemon(true);
        worker.start();
    }

    private void completeRestore(Stage owner, RecoveryRequest request, RestoreResult result) {
        String preservedPath = result.preservedStoragePath()
                .map(Path::toString)
                .orElse(I18n.get("startup.storageError.noPreviousStore"));
        Alert restored = new Alert(Alert.AlertType.INFORMATION,
                I18n.get("startup.storageError.restoreSuccess", preservedPath));
        FintraxUI.configureDialog(restored);
        restored.initOwner(owner);
        restored.setTitle(I18n.get("startup.storageError.restoreSuccess.title"));
        restored.setHeaderText(I18n.get("startup.storageError.restoreSuccess.title"));
        restored.showAndWait();

        try {
            restoredContext = FintraxSpringBootstrap.start(request.arguments());
            FintraxUI.showOnStage(owner, restoredContext);
            restoredFromBackup = true;
        } catch (Exception exception) {
            if (restoredContext != null) {
                restoredContext.close();
                restoredContext = null;
            }
            log.error("Fintrax could not start from the restored storage at {}", result.activeStoragePath(), exception);
            showStorageRecovery(owner, request.withFailure(exception));
        }
    }

    private String messageOf(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    @Override
    public void stop() {
        if (restoredContext != null) {
            restoredContext.close();
            restoredContext = null;
        }
        Platform.exit();
    }

    private record RecoveryRequest(Path storagePath, BackupConfiguration backupConfiguration,
                                   Throwable failure, String[] arguments) {
        RecoveryRequest withFailure(Throwable replacementFailure) {
            return new RecoveryRequest(storagePath, backupConfiguration, replacementFailure, arguments);
        }
    }
}
