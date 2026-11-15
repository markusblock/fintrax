package org.fintrax.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.service.BackupRestoreService;
import org.fintrax.service.BackupRestoreService.RestoreResult;
import org.fintrax.store.BackupConfiguration;
import org.fintrax.ui.FintraxUI;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
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
        recoveryRequest = new RecoveryRequest(storagePath, backupConfiguration, failure, List.of(args));
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
            showStorageInUseDialog(storageInUsePath);
            return;
        }
        showStorageRecovery(primaryStage, recoveryRequest);
    }

    private void showStorageInUseDialog(Path storagePath) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        FintraxUI.configureDialog(alert);
        alert.setTitle(I18n.get("app.name"));
        alert.setHeaderText(I18n.get("startup.storageInUse.title"));
        alert.setContentText(I18n.get("startup.storageInUse.message", storagePath));
        alert.showAndWait();
        Platform.exit();
    }

    private void showStorageRecovery(Stage owner, RecoveryRequest request) {
        BackupRestoreService restoreService = new BackupRestoreService();
        RecoveryRequest currentRequest = request;
        while (true) {
            List<Path> backups = listFullBackups(restoreService, currentRequest.backupConfiguration());

            Alert alert = createStorageErrorAlert(currentRequest.storagePath(), currentRequest.failure(), backups);
            ButtonType exit = alert.getButtonTypes().getLast();
            ButtonType restore = new ButtonType(I18n.get("startup.storageError.restore"));
            ButtonType changeBackup = new ButtonType(I18n.get("startup.storageError.changeBackup"));
            alert.getButtonTypes().setAll(backups.isEmpty()
                    ? List.of(changeBackup, exit) : List.of(restore, changeBackup, exit));
            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isEmpty() || choice.get() == exit) {
                Platform.exit();
                return;
            }
            if (choice.get() == changeBackup) {
                try {
                    DirectoryChooser chooser = new DirectoryChooser();
                    chooser.setTitle(I18n.get("settings.backup.choose.title"));
                    Path currentDestination = currentRequest.backupConfiguration().destination();
                    if (currentDestination.toFile().isDirectory()) {
                        chooser.setInitialDirectory(currentDestination.toFile());
                    }
                    File selectedFolder = chooser.showDialog(null);
                    if (selectedFolder == null) {
                        continue;
                    }
                    currentRequest.backupConfiguration().saveDestination(selectedFolder.toPath());
                    Alert restart = new Alert(Alert.AlertType.INFORMATION,
                            I18n.get("settings.backup.restartToResume"));
                    FintraxUI.configureDialog(restart);
                    restart.setTitle(I18n.get("settings.backup.failed.title"));
                    restart.setHeaderText(I18n.get("settings.backup.failed.title"));
                    restart.showAndWait();
                    Platform.exit();
                    return;
                } catch (RuntimeException exception) {
                    currentRequest = currentRequest.withFailure(exception);
                    continue;
                }
            }

            if (backups.isEmpty()) {
                continue;
            }
            ChoiceDialog<Path> backupChoice = new ChoiceDialog<>(backups.getFirst(), backups);
            FintraxUI.configureDialog(backupChoice);
            backupChoice.setTitle(I18n.get("startup.storageError.choose.title"));
            backupChoice.setHeaderText(I18n.get("startup.storageError.choose.header"));
            Optional<Path> selectedBackup = backupChoice.showAndWait();
            if (selectedBackup.isEmpty()) {
                continue;
            }
            startRestore(owner, currentRequest, selectedBackup.get(), restoreService);
            return;
        }
    }

    static Alert createStorageErrorAlert(Path storagePath, Throwable failure, List<Path> backups) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        FintraxUI.configureDialog(alert);
        alert.setTitle(I18n.get("startup.storageError.title"));
        alert.setHeaderText(I18n.get("startup.storageError.title"));
        String message = I18n.get("startup.storageError.message", storagePath, messageOf(failure));
        if (backups.isEmpty()) {
            message += "\n" + I18n.get("startup.storageError.noBackups");
        }
        alert.setContentText(message);

        ButtonType exit = new ButtonType(I18n.get("startup.storageError.exit"));
        ButtonType restore = new ButtonType(I18n.get("startup.storageError.restore"));
        alert.getButtonTypes().setAll(backups.isEmpty() ? List.of(exit) : List.of(restore, exit));
        return alert;
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
        progress.setTitle(I18n.get("startup.storageError.restoring.title"));
        progress.setHeaderText(I18n.get("startup.storageError.restoring.title"));
        progress.getDialogPane().setContent(new VBox(12,
                new ProgressIndicator(), new Label(I18n.get("startup.storageError.restoring.message"))));
        ButtonType restoring = new ButtonType(I18n.get("startup.storageError.restoring.button"));
        progress.getDialogPane().getButtonTypes().setAll(restoring);
        progress.getDialogPane().lookupButton(restoring).setDisable(true);
        progress.setOnCloseRequest(javafx.event.Event::consume);
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
        String preservedContinuousPath = result.preservedContinuousBackupPath()
                .map(Path::toString)
                .orElse(I18n.get("startup.storageError.noPreviousMirror"));
        Alert restored = new Alert(Alert.AlertType.INFORMATION,
                I18n.get("startup.storageError.restoreSuccess", preservedPath, preservedContinuousPath));
        FintraxUI.configureDialog(restored);
        restored.setTitle(I18n.get("startup.storageError.restoreSuccess.title"));
        restored.setHeaderText(I18n.get("startup.storageError.restoreSuccess.title"));
        restored.showAndWait();

        try {
            restoredContext = FintraxSpringBootstrap.start(request.arguments().toArray(String[]::new));
            FintraxUI.showOnStage(owner, restoredContext);
            markRestoredFromBackup();
        } catch (Exception exception) {
            if (restoredContext != null) {
                restoredContext.close();
                restoredContext = null;
            }
            log.error("Fintrax could not start from the restored storage at {}", result.activeStoragePath(), exception);
            showStorageRecovery(owner, request.withFailure(exception));
        }
    }

    private static String messageOf(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static void markRestoredFromBackup() {
        restoredFromBackup = true;
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
                                   Throwable failure, List<String> arguments) {
        RecoveryRequest {
            arguments = List.copyOf(arguments);
        }

        RecoveryRequest withFailure(Throwable replacementFailure) {
            return new RecoveryRequest(storagePath, backupConfiguration, replacementFailure, arguments);
        }
    }
}
