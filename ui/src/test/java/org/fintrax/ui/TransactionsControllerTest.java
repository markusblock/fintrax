package org.fintrax.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionsControllerTest extends AbstractUITest {
    @Override
    public void start(Stage stage) throws Exception {
        Category category = applicationContext.getBean(CategoryService.class)
                .createCategory("Cell category " + System.nanoTime(), null, null);
        org.fintrax.model.Label label = applicationContext.getBean(LabelService.class)
                .createLabel("Cell label " + System.nanoTime(), null, null);
        applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                .originalPayee("Cell popup test")
                .categoryId(category.getId())
                .labelIds(Set.of(label.getId()))
                .bookingDate(LocalDate.now())
                .amount(BigDecimal.ONE)
                .build());
        for (int i = 0; i < 2; i++) {
            applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                    .originalPayee("Context menu test " + i + " " + System.nanoTime())
                    .bookingDate(LocalDate.now().minusDays(i + 1))
                    .amount(BigDecimal.valueOf(i + 2))
                    .build());
        }

        Parent root = applicationContext.getBean(ViewLoader.class).load("transactions");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void doubleClickingCategoryCellOpensSetCategoryDialog() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        TableColumn<?, ?> categoryColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.category").equals(column.getText()))
                .findFirst()
                .orElseThrow();

        doubleClickOn(cellFor(table, categoryColumn));

        assertNotNull(lookup(I18n.get("dialog.bulkCategory.header", 1)).query());
        interact(lookup("#cancelCategoryButton").<Button>query()::fire);
    }

    @Test
    void doubleClickingLabelsCellOpensAddLabelDialog() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        TableColumn<?, ?> labelsColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.labels").equals(column.getText()))
                .findFirst()
                .orElseThrow();

        doubleClickOn(cellFor(table, labelsColumn));

        assertNotNull(lookup(I18n.get("dialog.bulkLabelAdd.header", 1)).query());
        interact(lookup("#cancelLabelButton").<Button>query()::fire);
    }

    @Test
    void transactionTableAllowsMultipleSelection() {
        TableView<Transaction> table = lookup("#transactionTable").query();

        assertEquals(SelectionMode.MULTIPLE, table.getSelectionModel().getSelectionMode());
    }

    @Test
    void bulkCategoryEditKeepsAllSelectedTransactionsSelected() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        interact(() -> {
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(0);
            table.getSelectionModel().select(1);
        });
        List<Long> selectedIds = table.getSelectionModel().getSelectedItems().stream()
                .map(Transaction::getId)
                .toList();

        rightClickOn(cellForRow(table, 0));
        assertEquals(List.of(0, 1), table.getSelectionModel().getSelectedIndices().stream().sorted().toList());
        Platform.runLater(() -> rowFor(table, 0).getContextMenu().getItems().get(0).fire());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup(I18n.get("dialog.bulkCategory.header", 2)).tryQuery().isPresent());
        assertNotNull(lookup(I18n.get("dialog.bulkCategory.header", 2)).query());
        acceptBulkEditDialog();

        assertEquals(selectedIds, table.getSelectionModel().getSelectedItems().stream()
                .map(Transaction::getId)
                .toList());
    }

    @Test
    void bulkLabelEditKeepsAllSelectedTransactionsSelected() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        interact(() -> {
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(0);
            table.getSelectionModel().select(1);
        });
        List<Long> selectedIds = table.getSelectionModel().getSelectedItems().stream()
                .map(Transaction::getId)
                .toList();

        rightClickOn(cellForRow(table, 0));
        Platform.runLater(() -> rowFor(table, 0).getContextMenu().getItems().get(1).fire());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup(I18n.get("dialog.bulkLabelAdd.header", 2)).tryQuery().isPresent());
        acceptBulkEditDialog();

        assertEquals(selectedIds, table.getSelectionModel().getSelectedItems().stream()
                .map(Transaction::getId)
                .toList());
    }

    @Test
    void detailSaveIsDisabledWhenMultipleTransactionsAreSelected() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        Button saveButton = lookup("#detailSaveButton").query();

        interact(() -> {
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(0);
        });
        assertFalse(saveButton.isDisabled());

        interact(() -> table.getSelectionModel().select(1));

        assertTrue(saveButton.isDisabled());
    }

    @Test
    void rightClickingSelectedRowKeepsMultipleSelection() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        interact(() -> {
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(0);
            table.getSelectionModel().select(1);
        });

        rightClickOn(cellForRow(table, 0));

        assertEquals(List.of(0, 1), table.getSelectionModel().getSelectedIndices().stream().sorted().toList());
        press(KeyCode.ESCAPE);
    }

    @Test
    void rightClickingUnselectedRowSelectsOnlyThatRow() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        interact(() -> {
            table.getSelectionModel().clearSelection();
            table.getSelectionModel().select(0);
            table.getSelectionModel().select(1);
        });

        rightClickOn(cellForRow(table, 2));

        assertEquals(List.of(2), table.getSelectionModel().getSelectedIndices().stream().sorted().toList());
        press(KeyCode.ESCAPE);
    }

    @Test
    void transactionRowContextMenuContainsCategoryAndLabelActions() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        TableRow<Transaction> row = rowFor(table, 0);
        ContextMenu contextMenu = row.getContextMenu();

        assertNotNull(contextMenu);
        assertEquals(List.of(
                        I18n.get("contextMenu.setCategory"),
                        I18n.get("contextMenu.addLabel"),
                        I18n.get("contextMenu.removeLabel")),
                contextMenu.getItems().stream().map(MenuItem::getText).toList());
        assertTrue(contextMenu.getItems().stream().allMatch(item -> item.getOnAction() != null));
    }

    private Node cellFor(TableView<Transaction> table, TableColumn<?, ?> column) {
        return table.lookupAll(".table-cell").stream()
                .filter(node -> node instanceof TableCell<?, ?>)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == column)
                .findFirst()
                .orElseThrow();
    }

    private void acceptBulkEditDialog() {
        interact(() -> lookup(".dialog-pane .button").queryAll().stream()
                .map(Button.class::cast)
                .filter(button -> "OK".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .fire());
    }

    private Node cellForRow(TableView<Transaction> table, int rowIndex) {
        return table.lookupAll(".table-cell").stream()
                .filter(node -> node instanceof TableCell<?, ?>)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getIndex() == rowIndex)
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private TableRow<Transaction> rowFor(TableView<Transaction> table, int rowIndex) {
        return table.lookupAll(".table-row-cell").stream()
                .filter(node -> node instanceof TableRow<?> row && row.getIndex() == rowIndex)
                .map(node -> (TableRow<Transaction>) node)
                .findFirst()
                .orElseThrow();
    }
}
