package org.fintrax.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        press(KeyCode.ESCAPE);
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
        press(KeyCode.ESCAPE);
    }

    private Node cellFor(TableView<Transaction> table, TableColumn<?, ?> column) {
        return table.lookupAll(".table-cell").stream()
                .filter(node -> node instanceof TableCell<?, ?>)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == column)
                .findFirst()
                .orElseThrow();
    }
}
