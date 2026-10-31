package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.service.SettingsService;
import org.fintrax.service.hibiscus.HibiscusXmlImporter;
import org.fintrax.ui.AbstractUITest;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.ViewLoader;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsControllerTest extends AbstractUITest {
    private SettingsController settingsController;

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("settings");
        settingsController = (SettingsController) root.getProperties().get(SettingsController.class.getName());
        Scene scene = new Scene(root, 1024, 768);
        FintraxUI.configureScene(scene);
        FintraxUI.applyTheme(scene, "light");
        stage.setScene(scene);
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

    @Test
    void changingLanguageSavesImmediately() {
        interact(() -> lookup("#languageCombo").<ComboBox<String>>query().setValue("Deutsch"));

        assertEquals("de", applicationContext.getBean(SettingsService.class).getLanguage());
    }

    @Test
    void changingThemeSavesImmediately() {
        interact(() -> lookup("#themeCombo").<ComboBox<String>>query().setValue("Dark"));

        assertEquals("dark", applicationContext.getBean(SettingsService.class).getTheme());
    }

    @Test
    void credentialTableMenuPersistsVisibilityAndOrder() {
        TableView<SettingsController.CredentialRow> table = lookup("#credentialTable").query();
        MenuButton columnMenu = lookup("#columnMenuButton").query();

        assertSame(columnMenu, table.getColumns().getLast().getGraphic());
        assertEquals(List.of("settings.credential.account", "settings.credential.status")
                        .stream().map(I18n::get).toList(),
                columnMenu.getItems().stream().map(MenuItem::getText).toList());

        var originalColumnOrder = List.copyOf(table.getColumns());
        CheckMenuItem statusItem = (CheckMenuItem) columnMenu.getItems().get(1);
        try {
            interact(() -> statusItem.setSelected(false));
            assertFalse(table.getColumns().get(1).isVisible());
            assertTrue(applicationContext.getBean(SettingsService.class)
                    .getSettingValue("settings.credentials.columns").contains("status=false"));

            interact(() -> {
                var accountColumn = table.getColumns().remove(0);
                table.getColumns().add(1, accountColumn);
            });
            assertEquals("status,account", applicationContext.getBean(SettingsService.class)
                    .getSettingValue("settings.credentials.columns.order"));
        } finally {
            interact(() -> {
                statusItem.setSelected(true);
                table.getColumns().setAll(originalColumnOrder);
            });
        }
    }
}
