package org.fintrax.app;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.fintrax.config.I18n;

import java.nio.file.Path;
import java.util.Locale;

public final class StartupErrorApplication extends Application {
    public static void showStorageInUse(Path storagePath) {
        Application.launch(StartupErrorApplication.class, storagePath.toString());
    }

    @Override
    public void start(Stage primaryStage) {
        Path storagePath = Path.of(getParameters().getRaw().getFirst());
        Locale startupLocale = Locale.GERMAN.getLanguage().equals(Locale.getDefault().getLanguage())
                ? Locale.GERMAN : Locale.ENGLISH;
        I18n.setLocale(startupLocale);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.get("app.name"));
        alert.setHeaderText(I18n.get("startup.storageInUse.title"));
        alert.setContentText(I18n.get("startup.storageInUse.message", storagePath));
        alert.showAndWait();
    }
}
