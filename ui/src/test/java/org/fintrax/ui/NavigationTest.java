package org.fintrax.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationTest extends AbstractUITest {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1024, 768);
        stage.setScene(scene);
        stage.show();
    }

    private void waitForPulse() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testInitialViewLoads() {
        StackPane contentArea = lookup("#contentArea").query();
        
        assertTrue(contentArea.getChildren().size() > 0,
                "Content area should have initial view loaded");
    }

    @Test
    void testNavigationClicks() {
        VBox sidebar = lookup("#sidebar").query();
        StackPane contentArea = lookup("#contentArea").query();

        int buttonIndex = 0;
        for (Node child : sidebar.getChildren()) {
            if (child instanceof Button) {
                if (buttonIndex >= 2) {
                    Button button = (Button) child;
                    
                    clickOn(button);
                    waitForPulse();
                    
                    assertTrue(contentArea.getChildren().size() > 0,
                            "Content area should have children after clicking: " + button.getText());
                }
                buttonIndex++;
            }
        }
    }

    @Test
    void testSidebarToggle() {
        VBox sidebar = lookup("#sidebar").query();
        Button toggleButton = lookup("#toggleSidebarButton").query();
        assertNotNull(toggleButton, "Toggle button should exist");
        
        double initialWidth = sidebar.getPrefWidth();
        assertEquals(200.0, initialWidth, "Initial sidebar width should be 200");

        interact(() -> toggleButton.fire());
        waitForPulse();
        
        double newWidth = sidebar.getPrefWidth();
        assertEquals(60.0, newWidth, "Sidebar width should be 60 after toggle");
    }
}
