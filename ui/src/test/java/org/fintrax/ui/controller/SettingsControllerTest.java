package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.fintrax.service.hibiscus.HibiscusXmlImporter;
import org.fintrax.ui.AbstractUITest;
import org.fintrax.ui.ViewLoader;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsControllerTest extends AbstractUITest {
    private SettingsController settingsController;

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("settings");
        settingsController = (SettingsController) root.getProperties().get(SettingsController.class.getName());
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void successfulImportReportOffersSharedApplyRulesReview() {
        Platform.runLater(() -> settingsController.showImportSuccess(
                new HibiscusXmlImporter.ImportResult(1, 2, 3, 4, 5)));
        WaitForAsyncUtils.waitForFxEvents();

        Button applyRulesButton = lookup("#applyRulesAfterImport").query();
        assertTrue(applyRulesButton.isVisible());
        clickOn(applyRulesButton);

        Label emptyResult = lookup("#ruleReviewEmptyResult").query();
        assertTrue(emptyResult.isVisible());
        clickOn(lookup("#cancelRuleReviewButton").<Button>query());
    }
}
