package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.fintrax.service.SettingsService;
import org.fintrax.service.hibiscus.HibiscusXmlImporter;
import org.fintrax.ui.AbstractUITest;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.ViewLoader;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void credentialTableSortOrderSurvivesViewReload() throws Exception {
        TableView<SettingsController.CredentialRow> table = lookup("#credentialTable").query();
        interact(() -> {
            table.getItems().setAll(new SettingsController.CredentialRow(1L, "Zulu", "Stored"),
                    new SettingsController.CredentialRow(2L, "Alpha", "Missing"));
            table.getColumns().get(0).setSortType(TableColumn.SortType.ASCENDING);
            table.getSortOrder().add(table.getColumns().get(0));
        });
        assertEquals("Alpha", table.getItems().getFirst().getAccountName());
        assertEquals("account=ASCENDING", applicationContext.getBean(SettingsService.class)
                .getSettingValue("settings.credentials.sort"));

        Parent restoredRoot = applicationContext.getBean(ViewLoader.class).load("settings");
        Stage stage = (Stage) table.getScene().getWindow();
        interact(() -> stage.getScene().setRoot(restoredRoot));

        TableView<SettingsController.CredentialRow> restoredTable = lookup("#credentialTable").query();
        assertEquals(1, restoredTable.getSortOrder().size());
        assertSame(restoredTable.getColumns().get(0), restoredTable.getSortOrder().getFirst());
        interact(() -> restoredTable.getSortOrder().clear());
    }

}
