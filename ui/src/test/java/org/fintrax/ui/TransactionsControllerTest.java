package org.fintrax.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionsControllerTest extends AbstractUITest {
    private String categoryName;
    private String labelName;

    @Override
    public void start(Stage stage) throws Exception {
        categoryName = "Cell category " + System.nanoTime();
        Category category = applicationContext.getBean(CategoryService.class)
                .createCategory(categoryName, null, null);
        labelName = "Cell label " + System.nanoTime();
        org.fintrax.model.Label label = applicationContext.getBean(LabelService.class)
                .createLabel(labelName, null, null);
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
        interact(lookup("#cancelCategoryButton").<Button>query()::fire);
    }

    @Test
    void categoryAssignmentDialogFiltersCategoriesBySearchText() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        TableColumn<?, ?> categoryColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.category").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        doubleClickOn(cellFor(table, categoryColumn));

        ComboBox<Category> categoryCombo = lookup("#categorySearchCombo").query();
        TextField searchField = lookup("#search").query();
        assertTrue(searchField.isFocused());
        interact(() -> searchField.setText(categoryName));

        ComboBox<?> filteredCombo = lookup("#filtered").query();
        assertEquals(1, filteredCombo.getItems().size());
        assertEquals(categoryName, ((Category) filteredCombo.getItems().getFirst()).getName());
        interact(() -> filteredCombo.getSelectionModel().selectFirst());
        assertEquals(categoryName, categoryCombo.getValue().getName());
        press(KeyCode.ESCAPE);
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
    void labelAssignmentDialogFiltersLabelsBySearchText() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        TableColumn<?, ?> labelsColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.labels").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        doubleClickOn(cellFor(table, labelsColumn));

        ComboBox<org.fintrax.model.Label> labelCombo = lookup("#labelSearchCombo").query();
        TextField searchField = lookup("#search").query();
        assertTrue(searchField.isFocused());
        interact(() -> searchField.setText(labelName));

        ComboBox<?> filteredCombo = lookup("#filtered").query();
        assertEquals(1, filteredCombo.getItems().size());
        assertEquals(labelName, ((org.fintrax.model.Label) filteredCombo.getItems().getFirst()).getName());
        interact(() -> filteredCombo.getSelectionModel().selectFirst());
        assertEquals(labelName, labelCombo.getValue().getName());
        press(KeyCode.ESCAPE);
        interact(lookup("#cancelLabelButton").<Button>query()::fire);
    }

    @Test
    void labelRemovalDialogAlsoOffersSearchableDropdown() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        interact(() -> table.getSelectionModel().select(0));
        Button removeButton = lookup("#bulkLabelRemoveButton").query();
        assertEquals(1, table.getSelectionModel().getSelectedItems().size());
        assertFalse(removeButton.isDisabled());
        clickOn(removeButton);

        assertNotNull(lookup(I18n.get("dialog.bulkLabelRemove.header", 1)).query());
        assertTrue(lookup("#search").query().isFocused());
        interact(lookup("#cancelLabelButton").<Button>query()::fire);
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
