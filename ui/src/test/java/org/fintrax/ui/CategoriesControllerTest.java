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
import javafx.scene.control.TableRow;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TableView;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoriesControllerTest extends AbstractUITest {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("categories");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void selectingCategoryShowsTransactionsFromItsSubtree() throws Exception {
        Category category = categoryService().createCategory("Transactions " + System.nanoTime(), null, null);
        Category childCategory = categoryService().createCategory("Child " + System.nanoTime(), category.getId(), null);
        Category otherCategory = categoryService().createCategory("Other " + System.nanoTime(), null, null);

        createTransaction(category.getId(), "Parent transaction", "20", LocalDate.of(2025, 2, 1));
        createTransaction(childCategory.getId(), "Child transaction", "7", LocalDate.of(2025, 2, 2));
        createTransaction(otherCategory.getId(), "Unrelated", "100", LocalDate.of(2025, 3, 1));

        reloadCategoriesView();
        TreeView<String> tree = lookup("#categoryTree").query();
        interact(() -> tree.getSelectionModel().select(treeItem(tree.getRoot(), category.getName())));
        TableView<Transaction> table = lookup("#categoryTransactionsTable").query();

        assertEquals(2, table.getItems().size());
        assertTrue(table.getItems().stream()
                .allMatch(transaction -> Set.of(category.getId(), childCategory.getId())
                        .contains(transaction.getCategoryId())));

        interact(() -> tree.getSelectionModel().select(tree.getRoot()));
        assertTrue(table.getItems().isEmpty());
    }

    @Test
    void categoryTransactionsSupportSameMultiSelectContextActionsAsTransactionsView() throws Exception {
        Category category = categoryService().createCategory("Bulk " + System.nanoTime(), null, null);
        createTransaction(category.getId(), "First bulk transaction", "20", LocalDate.of(2025, 2, 1));
        createTransaction(category.getId(), "Second bulk transaction", "30", LocalDate.of(2025, 2, 2));
        applicationContext.getBean(LabelService.class).createLabel("Bulk label " + System.nanoTime(), null, null);

        reloadCategoriesView();
        TreeView<String> tree = lookup("#categoryTree").query();
        interact(() -> tree.getSelectionModel().select(treeItem(tree.getRoot(), category.getName())));
        TableView<Transaction> table = lookup("#categoryTransactionsTable").query();

        assertEquals(SelectionMode.MULTIPLE, table.getSelectionModel().getSelectionMode());
        assertEquals(2, table.getItems().size());
        ContextMenu contextMenu = rowFor(table, 0).getContextMenu();
        assertNotNull(contextMenu);
        assertEquals(List.of(
                        I18n.get("contextMenu.setCategory"),
                        I18n.get("contextMenu.addLabel"),
                        I18n.get("contextMenu.removeLabel")),
                contextMenu.getItems().stream().map(MenuItem::getText).toList());

        interact(() -> {
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

    private TreeItem<String> treeItem(TreeItem<String> parent, String value) {
        return parent.getChildren().stream()
                .filter(item -> item.getValue().equals(value))
                .findFirst()
                .orElseThrow();
    }

    private void reloadCategoriesView() throws Exception {
        Stage stage = (Stage) lookup("#categoryTree").query().getScene().getWindow();
        Parent root = applicationContext.getBean(ViewLoader.class).load("categories");
        interact(() -> stage.getScene().setRoot(root));
    }

    private Transaction createTransaction(Long categoryId, String payee, String amount, LocalDate bookingDate) {
        return applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                .categoryId(categoryId)
                .originalPayee(payee)
                .amount(new BigDecimal(amount))
                .bookingDate(bookingDate)
                .build());
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

    private void acceptBulkEditDialog() {
        interact(() -> lookup(".dialog-pane .button").queryAll().stream()
                .map(Button.class::cast)
                .filter(button -> "OK".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .fire());
    }

    private CategoryService categoryService() {
        return applicationContext.getBean(CategoryService.class);
    }
}
