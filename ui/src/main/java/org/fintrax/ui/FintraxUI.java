package org.fintrax.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.service.SettingsService;
import org.springframework.context.ApplicationContext;

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
        primaryStage.show();

        log.info("Fintrax UI started");
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
