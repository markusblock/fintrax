package org.fintrax.ui;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("Starting Fintrax UI");

        SettingsService settingsService = getApplicationContext().getBean(SettingsService.class);
        I18n.setLocale(settingsService.getLocale());

        Parent root = getApplicationContext().getBean(ViewLoader.class).load("main");

        Scene scene = new Scene(root, MIN_WIDTH, MIN_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/css/fintrax.css").toExternalForm());
        applyTheme(scene, settingsService.getTheme());

        primaryStage.setTitle(TITLE);
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

    public static void main(String[] args) {
        Application.launch(FintraxUI.class, args);
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
        String darkStylesheet = FintraxUI.class.getResource("/css/dark.css").toExternalForm();
        scene.getStylesheets().remove(darkStylesheet);
        if ("dark".equals(theme)) {
            scene.getStylesheets().add(darkStylesheet);
        }
    }
}
