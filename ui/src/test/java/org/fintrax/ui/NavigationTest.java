package org.fintrax.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationTest extends AbstractUITest {

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("main");

        Scene scene = new Scene(root, 1024, 768);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void navigationLoadsTheSelectedViewController() {
        StackPane contentArea = lookup("#contentArea").query();
        assertLoadedController(contentArea, "org.fintrax.ui.controller.BankAccountsController");

        Map<String, String> navigationTargets = Map.of(
                "#bankAccountsButton", "org.fintrax.ui.controller.BankAccountsController",
                "#transactionsButton", "org.fintrax.ui.controller.TransactionsController",
                "#categoriesButton", "org.fintrax.ui.controller.CategoriesController",
                "#rulesButton", "org.fintrax.ui.controller.RulesController",
                "#labelsButton", "org.fintrax.ui.controller.LabelsController",
                "#activityLogButton", "org.fintrax.ui.controller.ActivityLogController",
                "#settingsButton", "org.fintrax.ui.controller.SettingsController");
        for (Map.Entry<String, String> target : navigationTargets.entrySet()) {
            interact(lookup(target.getKey()).<Button>query()::fire);
            assertLoadedController(contentArea, target.getValue());
        }
    }

    private void assertLoadedController(StackPane contentArea, String controllerClassName) {
        assertEquals(1, contentArea.getChildren().size());
        assertTrue(contentArea.getChildren().getFirst().getProperties().containsKey(controllerClassName));
    }

    @Test
    void testSidebarToggle() {
        VBox sidebar = lookup("#sidebar").query();
        Button toggleButton = lookup("#toggleSidebarButton").query();
        double initialWidth = sidebar.getPrefWidth();
        assertEquals(200.0, initialWidth);

        interact(() -> toggleButton.fire());
        double newWidth = sidebar.getPrefWidth();
        assertEquals(60.0, newWidth);
    }
}
