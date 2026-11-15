package org.fintrax.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.service.BackupService;
import org.fintrax.service.SettingsService;
import org.fintrax.store.BackupConfiguration;
import org.fintrax.ui.controller.SessionViewState;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public class FintraxUI extends Application {
    private static ApplicationContext applicationContext;
    private static final String TITLE = "Fintrax";
    private static final double MIN_WIDTH = 1024;
    private static final double MIN_HEIGHT = 768;
    private static final String DARK_THEME_STYLE_CLASS = "theme-dark";
    private static final String FINTRAX_STYLESHEET = FintraxUI.class.getResource("/css/fintrax.css").toExternalForm();
    private static final String PRIMER_LIGHT_STYLESHEET = new PrimerLight().getUserAgentStylesheet();
    private static final String PRIMER_DARK_STYLESHEET = new PrimerDark().getUserAgentStylesheet();
    private static boolean darkThemeEnabled;

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("Starting Fintrax UI");

        SettingsService settingsService = getApplicationContext().getBean(SettingsService.class);
        getApplicationContext().getBean(SessionViewState.class).clear();
        I18n.setLocale(settingsService.getLocale());

        Parent root = getApplicationContext().getBean(ViewLoader.class).load("main");

        Scene scene = new Scene(root, MIN_WIDTH, MIN_HEIGHT);
        configureScene(scene);
        applyTheme(scene, settingsService.getTheme());

        primaryStage.setTitle(TITLE);
        ApplicationIcon.applyTo(primaryStage);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        WindowGeometry.restore(primaryStage, settingsService);
        primaryStage.setOnCloseRequest(event -> WindowGeometry.save(primaryStage, settingsService));
        primaryStage.show();

        getApplicationContext().getBean(BackupService.class).start(
                failure -> Platform.runLater(() -> promptBackupRecovery(primaryStage, failure)));

        log.info("Fintrax UI started");
    }

    private void promptBackupRecovery(Stage owner, Throwable failure) {
        BackupConfiguration backupConfiguration = getApplicationContext().getBean(BackupConfiguration.class);
        while (owner.isShowing()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            configureDialog(alert);
            alert.initOwner(owner);
            alert.setTitle(I18n.get("settings.backup.failed.title"));
            alert.setHeaderText(I18n.get("settings.backup.failed.title"));
            alert.setContentText(I18n.get("settings.backup.failed", failure.getMessage()));
            ButtonType chooseFolder = new ButtonType(I18n.get("settings.backup.choose"));
            ButtonType exitFintrax = new ButtonType(I18n.get("settings.backup.exit"));
            alert.getButtonTypes().setAll(chooseFolder, exitFintrax);
            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isEmpty() || choice.get() == exitFintrax) {
                owner.close();
                Platform.exit();
                return;
            }

            try {
                Path currentDestination = backupConfiguration.destination();
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle(I18n.get("settings.backup.choose.title"));
                if (currentDestination.toFile().isDirectory()) {
                    chooser.setInitialDirectory(currentDestination.toFile());
                }
                File selectedFolder = chooser.showDialog(owner);
                if (selectedFolder == null) {
                    continue;
                }
                backupConfiguration.saveDestination(selectedFolder.toPath());
                Alert restart = new Alert(Alert.AlertType.INFORMATION,
                        I18n.get("settings.backup.restartToResume"));
                configureDialog(restart);
                restart.initOwner(owner);
                restart.setTitle(I18n.get("settings.backup.failed.title"));
                restart.setHeaderText(I18n.get("settings.backup.failed.title"));
                restart.showAndWait();
                owner.close();
                Platform.exit();
                return;
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
    }

    @Override
    public void stop() {
        log.info("Fintrax UI stopping");
        javafx.application.Platform.exit();
    }

    public static void launch(ApplicationContext context, String... args) {
        configureContext(context);
        Application.launch(FintraxUI.class, args);
    }

    private static void configureContext(ApplicationContext context) {
        applicationContext = context;
    }

    private static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("FintraxUI application context is not configured");
        }
        return applicationContext;
    }

    public static void applyTheme(Scene scene, String theme) {
        darkThemeEnabled = "dark".equals(theme);
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        try {
            // JavaFX resolves user-agent stylesheets via the thread context classloader.
            // On macOS, UI events can arrive on AppKit Thread with no context loader.
            currentThread.setContextClassLoader(FintraxUI.class.getClassLoader());
            Application.setUserAgentStylesheet(darkThemeEnabled
                    ? PRIMER_DARK_STYLESHEET
                    : PRIMER_LIGHT_STYLESHEET);
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
        applyThemeClass(scene.getRoot());
    }

    public static void configureScene(Scene scene) {
        if (!scene.getStylesheets().contains(FINTRAX_STYLESHEET)) {
            scene.getStylesheets().add(FINTRAX_STYLESHEET);
        }
        applyThemeClass(scene.getRoot());
    }

    public static void configureDialog(Dialog<?> dialog) {
        configureDialogPane(dialog.getDialogPane());
    }

    public static void configureDialogPane(DialogPane dialogPane) {
        if (!dialogPane.getStylesheets().contains(FINTRAX_STYLESHEET)) {
            dialogPane.getStylesheets().add(FINTRAX_STYLESHEET);
        }
        applyThemeClass(dialogPane);
    }

    private static void applyThemeClass(Parent root) {
        root.getStyleClass().remove(DARK_THEME_STYLE_CLASS);
        if (darkThemeEnabled) {
            root.getStyleClass().add(DARK_THEME_STYLE_CLASS);
        }
    }
}
