package org.fintrax.ui;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.fintrax.model.ActivityAction;
import org.fintrax.model.ActivityLog;
import org.fintrax.model.EntityType;
import org.fintrax.model.Label;
import org.fintrax.ui.control.LabelChipFactory;
import org.fintrax.ui.control.TableCellTooltips;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableCellTooltipsTest extends AbstractUITest {
    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
    private static final String LONG_DESCRIPTION = "Long activity description ".repeat(20).strip();

    private Parent root;

    @Override
    public void start(Stage stage) throws Exception {
        root = applicationContext.getBean(ViewLoader.class).load("activityLog");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void hoveringTruncatedCellShowsFullTextTooltip() {
        TableView<ActivityLog> table = lookup("#logTable").query();
        interact(() -> table.getItems().setAll(
                ActivityLog.builder().timestamp(TIMESTAMP).action(ActivityAction.CREATE)
                        .entityType(EntityType.ACCOUNT).entityId(1L).description(LONG_DESCRIPTION).build()));
        WaitForAsyncUtils.waitForFxEvents();

        TableCell<?, ?> longTextCell = cellWithItem(table, LONG_DESCRIPTION);
        assertNull(longTextCell.getTooltip());

        moveTo(longTextCell);
        sleep(600);
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(longTextCell.getTooltip());
        assertEquals(LONG_DESCRIPTION, longTextCell.getTooltip().getText());

        sleep(1400);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(longTextCell.getTooltip().isShowing());
    }

    @Test
    void hoveringFittingCellShowsNoTooltip() {
        TableView<ActivityLog> table = lookup("#logTable").query();
        interact(() -> table.getItems().setAll(
                ActivityLog.builder().timestamp(TIMESTAMP).action(ActivityAction.CREATE)
                        .entityType(EntityType.ACCOUNT).entityId(1L).description("short").build()));
        WaitForAsyncUtils.waitForFxEvents();

        TableCell<?, ?> timestampCell = cellWithItem(table, TIMESTAMP);
        moveTo(timestampCell);
        sleep(600);
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(timestampCell.getTooltip());
    }

    @Test
    void emptyTableCellHasNoTooltip() {
        TableView<ActivityLog> table = lookup("#logTable").query();
        interact(() -> table.getItems().setAll(
                ActivityLog.builder().timestamp(TIMESTAMP).action(ActivityAction.CREATE)
                        .entityType(EntityType.ACCOUNT).entityId(1L).description(LONG_DESCRIPTION).build()));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(TableCell.class::cast)
                .anyMatch(cell -> cell.isEmpty() && cell.getTooltip() == null));
    }

    @Test
    void hoveringTruncatedLabelChipsShowsFullLabelTextTooltip() {
        TableView<String> table = new TableView<>();
        table.setPrefSize(400, 400);
        TableColumn<String, String> chipsColumn = new TableColumn<>("Labels");
        chipsColumn.setPrefWidth(120);
        chipsColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue()));
        chipsColumn.setCellFactory(column -> new TableCell<>() {
            private final List<Label> labels = List.of(
                    Label.builder().id(1L).name("Groceries and household supplies").color("#3B82F6").build(),
                    Label.builder().id(2L).name("Holidays and travel expenses").color("#22C55E").build(),
                    Label.builder().id(3L).name("Subscriptions and digital services").color("#EF4444").build());
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : LabelChipFactory.createAll(labels));
            }
        });
        table.getColumns().add(chipsColumn);
        TableCellTooltips.install(table);
        String joinedLabels = "Groceries and household supplies, Holidays and travel expenses, "
                + "Subscriptions and digital services";
        interact(() -> {
            table.getItems().add(joinedLabels);
            ((BorderPane) root).setCenter(table);
            BorderPane.setAlignment(table, Pos.CENTER);
        });
        WaitForAsyncUtils.waitForFxEvents();

        TableCell<?, ?> chipCell = cellWithItem(table, joinedLabels);
        assertNull(chipCell.getTooltip());

        moveTo(chipCell);
        sleep(600);
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(chipCell.getTooltip());
        assertEquals(joinedLabels, chipCell.getTooltip().getText());
    }

    private TableCell<?, ?> cellWithItem(TableView<?> table, Object item) {
        return table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(TableCell.class::cast)
                .filter(cell -> item.equals(cell.getItem()))
                .findFirst()
                .orElseThrow();
    }
}