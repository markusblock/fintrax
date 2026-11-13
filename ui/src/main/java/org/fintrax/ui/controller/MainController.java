package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.ui.ViewLoader;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class MainController {
    private static final String ACTIVITY_LOG_VIEW = "activityLog";
    @FXML
    private BorderPane rootPane;

    @FXML
    private VBox sidebar;

    @FXML
    private StackPane contentArea;

    @FXML
    private Button toggleSidebarButton;
    @FXML
    private Button bankAccountsButton;
    @FXML
    private Button transactionsButton;
    @FXML
    private Button classificationsButton;
    @FXML
    private Button rulesButton;
    @FXML
    private Button activityLogButton;
    @FXML
    private Button settingsButton;

    private boolean sidebarExpanded = true;
    private String currentView;
    private final ViewLoader viewLoader;

    public MainController(ViewLoader viewLoader) {
        this.viewLoader = viewLoader;
    }

    @FXML
    public void initialize() {
        log.info("MainController initialized");
        rootPane.getProperties().put(MainController.class.getName(), this);
        refreshLocale();
        loadView("bankAccounts");
    }

    public void refreshLocale() {
        setNavigationLabel(bankAccountsButton, I18n.get("view.bankAccounts"));
        setNavigationLabel(transactionsButton, I18n.get("view.transactions"));
        setNavigationLabel(classificationsButton, I18n.get("view.classifications"));
        setNavigationLabel(rulesButton, I18n.get("view.rules"));
        setNavigationLabel(activityLogButton, I18n.get("view.activityLog"));
        setNavigationLabel(settingsButton, I18n.get("view.settings"));
    }

    @FXML
    private void onToggleSidebar() {
        sidebarExpanded = !sidebarExpanded;
        sidebar.setPrefWidth(sidebarExpanded ? 200 : 60);
        sidebar.getStyleClass().remove("collapsed");
        if (!sidebarExpanded) {
            sidebar.getStyleClass().add("collapsed");
        }
        refreshLocale();
    }

    private void setNavigationLabel(Button button, String label) {
        button.setAccessibleText(label);
        button.setText(sidebarExpanded ? label : "");
        button.setTooltip(sidebarExpanded ? null : new Tooltip(label));
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
    private void onClassifications() {
        loadView("classifications");
    }

    @FXML
    private void onRules() {
        loadView("rules");
    }

    @FXML
    private void onActivityLog() {
        loadView(ACTIVITY_LOG_VIEW);
    }

    public void showActivityLog() {
        loadView(ACTIVITY_LOG_VIEW);
    }

    @FXML
    private void onSettings() {
        loadView("settings");
    }

    private void loadView(String viewName) {
        try {
            Node view = viewLoader.load(viewName);
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            updateActiveNavigation(viewName);
            log.info("Loaded view: {}", viewName);
        } catch (Exception e) {
            log.error("Failed to load view: {}", viewName, e);
        }
        currentView = viewName;
    }

    private void updateActiveNavigation(String viewName) {
        Button activeButton = switch (viewName) {
            case "bankAccounts" -> bankAccountsButton;
            case "transactions" -> transactionsButton;
            case "classifications" -> classificationsButton;
            case "rules" -> rulesButton;
            case ACTIVITY_LOG_VIEW -> activityLogButton;
            case "settings" -> settingsButton;
            default -> null;
        };

        for (Button button : new Button[]{
                bankAccountsButton, transactionsButton, classificationsButton, rulesButton,
                activityLogButton, settingsButton}) {
            button.getStyleClass().remove("active");
        }
        if (activeButton != null) {
            activeButton.getStyleClass().add("active");
        }
    }

    public void reloadCurrentView() {
        if (currentView != null) {
            loadView(currentView);
        }
    }
}
