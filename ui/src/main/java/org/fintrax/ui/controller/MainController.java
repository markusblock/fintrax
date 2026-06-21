package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;

@Slf4j
public class MainController {
    @FXML
    private BorderPane rootPane;

    @FXML
    private VBox sidebar;

    @FXML
    private StackPane contentArea;

    @FXML
    private Button toggleSidebarButton;

    private boolean sidebarExpanded = true;

    @FXML
    public void initialize() {
        log.info("MainController initialized");
        loadView("bankAccounts");
    }

    @FXML
    private void onToggleSidebar() {
        sidebarExpanded = !sidebarExpanded;
        if (sidebarExpanded) {
            sidebar.setPrefWidth(200);
        } else {
            sidebar.setPrefWidth(60);
        }
    }

    @FXML
    private void onBankAccounts() {
        loadView("bankAccounts");
    }

    @FXML
    private void onTransactions() {
        loadView("transactions");
    }

    @FXML
    private void onCategories() {
        loadView("categories");
    }

    @FXML
    private void onRules() {
        loadView("rules");
    }

    @FXML
    private void onLabels() {
        loadView("labels");
    }

    @FXML
    private void onActivityLog() {
        loadView("activityLog");
    }

    @FXML
    private void onSettings() {
        loadView("settings");
    }

    private void loadView(String viewName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + viewName + ".fxml"));
            loader.setResources(I18n.getResourceBundle());
            Node view = (Node) loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            log.info("Loaded view: {}", viewName);
        } catch (Exception e) {
            log.error("Failed to load view: {}", viewName, e);
        }
    }
}
