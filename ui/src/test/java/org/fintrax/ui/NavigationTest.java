package org.fintrax.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.Transaction;
import org.fintrax.service.TransactionService;
import org.kordamp.ikonli.javafx.FontIcon;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationTest extends AbstractUITest {

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("main");

        Scene scene = new Scene(root, 1024, 768);
        FintraxUI.configureScene(scene);
        FintraxUI.applyTheme(scene, "light");
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
            Button navigationButton = lookup(target.getKey()).query();
            interact(navigationButton::fire);
            assertLoadedController(contentArea, target.getValue());
            assertTrue(navigationButton.getStyleClass().contains("active"));
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

    @Test
    void themeSwitchingUpdatesSceneThemeClass() {
        Scene scene = lookup("#rootPane").query().getScene();

        interact(() -> FintraxUI.applyTheme(scene, "light"));
        assertFalse(scene.getRoot().getStyleClass().contains("theme-dark"));

        interact(() -> FintraxUI.applyTheme(scene, "dark"));
        assertTrue(scene.getRoot().getStyleClass().contains("theme-dark"));
    }

    @Test
    void themeSwitchingWorksWhenEventThreadHasNoContextClassLoader() {
        Scene scene = lookup("#rootPane").query().getScene();

        interact(() -> {
            Thread eventThread = Thread.currentThread();
            ClassLoader originalClassLoader = eventThread.getContextClassLoader();
            eventThread.setContextClassLoader(null);
            try {
                assertDoesNotThrow(() -> FintraxUI.applyTheme(scene, "dark"));
                assertNull(eventThread.getContextClassLoader(), "theme switch should restore the event thread loader");
                assertTrue(scene.getRoot().getStyleClass().contains("theme-dark"));
            } finally {
                eventThread.setContextClassLoader(originalClassLoader);
            }
        });
    }

    @Test
    void newDialogsInheritCurrentThemeAndFintraxStyles() {
        Scene scene = lookup("#rootPane").query().getScene();
        interact(() -> FintraxUI.applyTheme(scene, "dark"));
        DialogPane[] pane = new DialogPane[1];
        interact(() -> {
            Dialog<Button> dialog = new Dialog<>();
            FintraxUI.configureDialog(dialog);
            pane[0] = dialog.getDialogPane();
        });

        assertTrue(pane[0].getStyleClass().contains("theme-dark"));
        assertTrue(pane[0].getStylesheets().stream().anyMatch(path -> path.endsWith("fintrax.css")));
    }

    @Test
    void navigationButtonsKeepLocalizedLabelsAlongsideFeatherIcons() {
        Map<String, String> navigationLabels = Map.of(
                "#bankAccountsButton", "view.bankAccounts",
                "#transactionsButton", "view.transactions",
                "#categoriesButton", "view.categories",
                "#rulesButton", "view.rules",
                "#labelsButton", "view.labels",
                "#activityLogButton", "view.activityLog",
                "#settingsButton", "view.settings");

        for (Map.Entry<String, String> entry : navigationLabels.entrySet()) {
            Button button = lookup(entry.getKey()).query();
            assertEquals(I18n.get(entry.getValue()), button.getText());
            assertTrue(button.getGraphic() instanceof FontIcon);
        }
    }

    @Test
    void transactionColumnsFitAtMinimumWindowWidth() {
        interact(lookup("#transactionsButton").<Button>query()::fire);
        TableView<?> table = lookup("#transactionTable").query();
        interact(() -> {
            table.applyCss();
            table.layout();
        });

        assertEquals(7, table.getColumns().size());
        assertTrue(table.getColumns().stream().allMatch(column -> column.isVisible()));
        double columnsWidth = table.getColumns().stream().mapToDouble(column -> column.getWidth()).sum();
        assertTrue(columnsWidth <= table.getWidth() + 1.0,
                "column width " + columnsWidth + " should fit table width " + table.getWidth());
        assertTrue(table.lookupAll(".scroll-bar").stream()
                        .filter(ScrollBar.class::isInstance)
                        .map(ScrollBar.class::cast)
                        .filter(bar -> bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL)
                        .noneMatch(ScrollBar::isVisible),
                "transaction table should not need horizontal scrolling at minimum window width");
    }

    @Test
    void syncStatusPanelStaysCompactAtMinimumWindowSize() {
        VBox syncStatusPane = lookup("#syncStatusPane").query();
        layout(lookup("#rootPane").query());

        assertTrue(syncStatusPane.getHeight() <= 140.0,
                "sync status panel should stay compact, got " + syncStatusPane.getHeight());
    }

    @Test
    void ruleColumnsFitAtMinimumWindowWidth() {
        interact(lookup("#rulesButton").<Button>query()::fire);
        TableView<?> table = lookup("#ruleTable").query();
        layout(table);

        double columnsWidth = table.getColumns().stream().mapToDouble(column -> column.getWidth()).sum();
        assertTrue(columnsWidth <= table.getWidth() + 1.0,
                "rule column width " + columnsWidth + " should fit table width " + table.getWidth());
    }

    @Test
    void categoryDateColumnKeepsEnoughWidthForFullDate() {
        interact(lookup("#categoriesButton").<Button>query()::fire);
        TableView<?> table = lookup("#categoryTransactionsTable").query();
        layout(table);

        assertTrue(table.getColumns().getFirst().getWidth() >= 100.0,
                "date column should remain wide enough for yyyy-MM-dd, got "
                        + table.getColumns().getFirst().getWidth());
    }

    @Test
    void transactionTableUsesAvailableHeightWhenDetailsAreClosed() {
        interact(lookup("#transactionsButton").<Button>query()::fire);
        TableView<?> table = lookup("#transactionTable").query();
        SplitPane splitPane = lookup("#splitPane").query();
        Button detailsButton = lookup(I18n.get("button.toggleDetail")).query();
        VBox detailPanel = lookup("#detailPanel").query();
        layout(splitPane);

        double tableHeightWithoutDetails = table.getHeight();
        assertFalse(detailPanel.isVisible());

        interact(detailsButton::fire);
        layout(splitPane);
        double tableHeightWithDetails = table.getHeight();
        assertTrue(detailPanel.isVisible());
        assertTrue(tableHeightWithoutDetails > tableHeightWithDetails + 80.0);

        interact(detailsButton::fire);
        layout(splitPane);
        assertFalse(detailPanel.isVisible());
        assertTrue(table.getHeight() > tableHeightWithDetails + 80.0);
    }

    @Test
    void selectedTransactionsUseDarkAccentAndKeepColumnBordersInBothThemes() {
        TransactionService transactionService = applicationContext.getBean(TransactionService.class);
        interact(() -> {
            transactionService.createTransaction(Transaction.builder()
                    .originalPayee("Border sample one")
                    .bookingDate(LocalDate.of(2026, 10, 21))
                    .amount(new BigDecimal("-10.00"))
                    .build());
            transactionService.createTransaction(Transaction.builder()
                    .originalPayee("Border sample two")
                    .bookingDate(LocalDate.of(2026, 10, 20))
                    .amount(new BigDecimal("-20.00"))
                    .build());
        });
        interact(lookup("#transactionsButton").<Button>query()::fire);
        TableView<?> table = lookup("#transactionTable").query();
        Color[] borderColors = new Color[4];
        Color[] stripedSelectionColors = new Color[2];

        for (int themeIndex = 0; themeIndex < 2; themeIndex++) {
            String theme = themeIndex == 0 ? "light" : "dark";
            int colorIndex = themeIndex * 2;
            interact(() -> {
                FintraxUI.applyTheme(table.getScene(), theme);
                table.getScene().getRoot().applyCss();
                table.getScene().getRoot().layout();
                table.getSelectionModel().clearSelection();
                table.getSelectionModel().select(1);
                table.getScene().getRoot().applyCss();
                table.getScene().getRoot().layout();
                stripedSelectionColors[colorIndex / 2] = rowBackground(table, 1);
                table.getSelectionModel().clearSelection();
                table.getSelectionModel().select(0);
                borderColors[colorIndex] = rightBorderColor(table, 0);
                borderColors[colorIndex + 1] = rightBorderColor(table, 1);
            });
            Color expectedSelectionColor = themeIndex == 0 ? Color.web("#0969da") : Color.web("#1f6feb");
            assertEquals(expectedSelectionColor, stripedSelectionColors[themeIndex],
                    "striped selected row should use a dark accent fill in " + theme + " theme");
            assertEquals(borderColors[themeIndex * 2 + 1], borderColors[themeIndex * 2],
                    "selected row border should match unselected row in " + theme + " theme");
        }
    }

    @Test
    void changingThemeFromSettingsUpdatesTheOpenWindowImmediately() {
        StackPane contentArea = lookup("#contentArea").query();
        Color previousBackground = firstBackgroundFill(contentArea).getFill() instanceof Color color
                ? color
                : null;
        assertNotNull(previousBackground);

        interact(lookup("#settingsButton").<Button>query()::fire);
        ComboBox<String> themeCombo = lookup("#themeCombo").query();
        for (String theme : new String[]{"Dark", "Light", "Dark"}) {
            interact(() -> themeCombo.setValue(theme));
            interact(() -> {
                contentArea.applyCss();
                contentArea.layout();
            });

            Color currentBackground = (Color) firstBackgroundFill(contentArea).getFill();
            assertNotEquals(previousBackground, currentBackground,
                    "content surface should update immediately after switching to " + theme);
            boolean darkTheme = "Dark".equals(theme);
            assertEquals(darkTheme,
                    contentArea.getScene().getRoot().getStyleClass().contains("theme-dark"));
            previousBackground = currentBackground;
        }
    }

    private BackgroundFill firstBackgroundFill(Region node) {
        return node.getBackground().getFills().getFirst();
    }

    private Color rightBorderColor(TableView<?> table, int rowIndex) {
        TableRow<?> row = table.lookupAll(".table-row-cell").stream()
                .filter(TableRow.class::isInstance)
                .map(node -> (TableRow<?>) node)
                .filter(candidate -> !candidate.isEmpty() && candidate.getIndex() == rowIndex)
                .findFirst()
                .orElseThrow();
        TableCell<?, ?> cell = (TableCell<?, ?>) row.lookup(".table-cell");
        assertNotNull(cell);
        assertNotNull(cell.getBorder());
        return (Color) cell.getBorder().getStrokes().getFirst().getRightStroke();
    }

    private Color rowBackground(TableView<?> table, int rowIndex) {
        TableRow<?> row = table.lookupAll(".table-row-cell").stream()
                .filter(TableRow.class::isInstance)
                .map(node -> (TableRow<?>) node)
                .filter(candidate -> !candidate.isEmpty() && candidate.getIndex() == rowIndex)
                .findFirst()
                .orElseThrow();
        return (Color) row.getBackground().getFills().getLast().getFill();
    }

    private void layout(Parent node) {
        interact(() -> {
            node.applyCss();
            node.layout();
        });
    }

}
