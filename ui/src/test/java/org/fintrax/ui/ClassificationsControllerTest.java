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
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.scene.control.TableView;
import javafx.scene.control.ColorPicker;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Label;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationsControllerTest extends AbstractUITest {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("classifications");
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

        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, category.getName())));
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();

        assertEquals(2, table.getItems().size());
        assertTrue(table.getItems().stream()
                .allMatch(transaction -> Set.of(category.getId(), childCategory.getId())
                        .contains(transaction.getCategoryId())));

        interact(() -> tree.getSelectionModel().select(
                treeItem(tree.getRoot(), I18n.get("classifications.categoriesRoot"))));
        assertTrue(table.getItems().isEmpty());
    }

    @Test
    void categoryTransactionsSupportSameMultiSelectContextActionsAsTransactionsView() throws Exception {
        Category category = categoryService().createCategory("Bulk " + System.nanoTime(), null, null);
        createTransaction(category.getId(), "First bulk transaction", "20", LocalDate.of(2025, 2, 1));
        createTransaction(category.getId(), "Second bulk transaction", "30", LocalDate.of(2025, 2, 2));
        applicationContext.getBean(LabelService.class).createLabel("Bulk label " + System.nanoTime(), null, null);

        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, category.getName())));
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();

        assertEquals(SelectionMode.MULTIPLE, table.getSelectionModel().getSelectionMode());
        assertEquals(2, table.getItems().size());
        ContextMenu contextMenu = rowFor(table, 0).getContextMenu();
        assertNotNull(contextMenu);
        assertEquals(List.of(
                        I18n.get("contextMenu.setCategory"),
                        I18n.get("contextMenu.setLabels")),
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
                () -> lookup("#pickerSearch").tryQuery().isPresent());
        acceptBulkEditDialog();

        assertEquals(selectedIds, table.getSelectionModel().getSelectedItems().stream()
                .map(Transaction::getId)
                .toList());
    }

    @Test
    void doubleClickingTransactionCategoryOpensCategoryPicker() throws Exception {
        Category category = categoryService().createCategory("Double click " + System.nanoTime(), null, null);
        createTransaction(category.getId(), "Category picker transaction", "20", LocalDate.of(2025, 2, 1));
        reloadClassificationsView();

        TreeView<String> tree = lookup("#classificationTree").query();
        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, category.getName())));
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
        TableColumn<?, ?> categoryColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.category").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        Node categoryCell = table.lookupAll(".table-cell").stream()
                .filter(node -> node instanceof TableCell<?, ?>)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == categoryColumn)
                .findFirst()
                .orElseThrow();

        doubleClickOn(categoryCell);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#pickerSearch").tryQuery().isPresent());
        assertEquals(category.getId(), lookup("#pickerList")
                .<javafx.scene.control.ListView<Category>>query().getSelectionModel()
                .getSelectedItem().getId());
        interact(lookup("#pickerCancel").<Button>query()::fire);
    }

    @Test
    void doubleClickingTransactionLabelsOpensLabelPicker() throws Exception {
        Label label = labelService().createLabel("Picker label " + System.nanoTime(), "#1976d2", null);
        Category category = categoryService().createCategory("Picker category " + System.nanoTime(), null, null);
        Transaction transaction = createTransaction(category.getId(), "Label picker transaction", "20",
                LocalDate.of(2025, 2, 1));
        applicationContext.getBean(TransactionService.class).updateLabels(transaction.getId(), Set.of(label.getId()));
        reloadClassificationsView();

        TreeView<String> tree = lookup("#classificationTree").query();
        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, category.getName())));
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
        TableColumn<?, ?> labelsColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.labels").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        Node labelsCell = table.lookupAll(".table-cell").stream()
                .filter(node -> node instanceof TableCell<?, ?>)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == labelsColumn)
                .findFirst()
                .orElseThrow();

        doubleClickOn(labelsCell);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#selectedLabelsPane").tryQuery().isPresent());
        FlowPane selectedLabels = lookup("#selectedLabelsPane").query();
        assertEquals(1, selectedLabels.getChildren().size());
        assertEquals(label.getName(), ((javafx.scene.control.Label)
                ((HBox) selectedLabels.getChildren().getFirst()).getChildren().getFirst()).getText());
        interact(lookup("#pickerCancel").<Button>query()::fire);
    }

    @Test
    void selectingLabelShowsOnlyItsTransactionsAndBothRootsShowNone() throws Exception {
        Label label = labelService().createLabel("Travel " + System.nanoTime(), "#1976d2", null);
        Category category = categoryService().createCategory("Label category " + System.nanoTime(), null, null);
        Transaction matching = createTransaction(category.getId(), "Labeled transaction", "20",
                LocalDate.of(2025, 2, 1));
        createTransaction(category.getId(), "Unlabeled transaction", "30", LocalDate.of(2025, 2, 2));
        applicationContext.getBean(TransactionService.class).updateLabels(matching.getId(), Set.of(label.getId()));

        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
        TreeItem<String> categoriesRoot = treeItem(tree.getRoot(), I18n.get("classifications.categoriesRoot"));
        TreeItem<String> labelsRoot = treeItem(tree.getRoot(), I18n.get("classifications.labelsRoot"));

        assertEquals(List.of(I18n.get("classifications.categoriesRoot"),
                        I18n.get("classifications.labelsRoot")),
                tree.getRoot().getChildren().stream().map(TreeItem::getValue).toList());

        interact(() -> tree.getSelectionModel().select(categoriesRoot));
        assertTrue(table.getItems().isEmpty());
        interact(() -> tree.getSelectionModel().select(labelsRoot));
        assertTrue(table.getItems().isEmpty());
        interact(() -> tree.getSelectionModel().select(treeItem(labelsRoot, label.getName())));

        assertEquals(List.of(matching.getId()), table.getItems().stream().map(Transaction::getId).toList());
        TableColumn<Transaction, ?> labelsColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.labels").equals(column.getText()))
                .map(column -> (TableColumn<Transaction, ?>) column)
                .findFirst()
                .orElseThrow();
        assertEquals(label.getName(), labelsColumn.getCellObservableValue(matching).getValue());
        TableCell<?, ?> labelsCell = table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == labelsColumn)
                .findFirst()
                .orElseThrow();
        assertTrue(labelsCell.getGraphic() instanceof FlowPane);
        FlowPane labelChips = (FlowPane) labelsCell.getGraphic();
        javafx.scene.control.Label chip = (javafx.scene.control.Label) labelChips.getChildren().getFirst();
        assertEquals(label.getName(), chip.getText());
        assertEquals(javafx.scene.paint.Color.web(label.getColor()),
                chip.getBackground().getFills().getFirst().getFill());
    }

    @Test
    void doubleClickingLabelOpensEditorAndSavesItsFields() throws Exception {
        Label label = labelService().createLabel("Editable label " + System.nanoTime(), "#1976d2", "Before");
        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        TreeItem<String> labelsRoot = treeItem(tree.getRoot(), I18n.get("classifications.labelsRoot"));
        TreeItem<String> labelItem = treeItem(labelsRoot, label.getName());
        TreeCell<?> labelCell = tree.lookupAll(".tree-cell").stream()
                .filter(TreeCell.class::isInstance)
                .map(node -> (TreeCell<?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTreeItem() == labelItem)
                .findFirst()
                .orElseThrow();
        assertTrue(labelCell.getGraphic() instanceof javafx.scene.control.Label);
        javafx.scene.control.Label labelChip = (javafx.scene.control.Label) labelCell.getGraphic();
        assertEquals(label.getName(), labelChip.getText());
        assertEquals(javafx.scene.paint.Color.web(label.getColor()),
                labelChip.getBackground().getFills().getFirst().getFill());

        doubleClickOn(labelCell);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#labelNameField").tryQuery().isPresent());
        TextField nameField = lookup("#labelNameField").query();
        TextField commentField = lookup("#labelCommentField").query();
        ColorPicker colorPicker = lookup("#labelColorPicker").query();
        interact(() -> {
            nameField.setText("Renamed label");
            commentField.setText("Updated comment");
            colorPicker.setValue(javafx.scene.paint.Color.RED);
            lookup("#labelSaveButton").<Button>query().fire();
        });

        Label saved = labelService().getLabel(label.getId()).orElseThrow();
        assertEquals("Renamed label", saved.getName());
        assertEquals("Updated comment", saved.getComment());
        assertEquals("#FF0000", saved.getColor());
    }

    @Test
    void addLabelContextMenuAddsLabelUnderAllLabels() throws Exception {
        Category category = categoryService().createCategory("Context menu category " + System.nanoTime(), null, null);
        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        TreeItem<String> categoryItem = categoryTreeItem(tree, category.getName());
        TreeCell<?> categoryCell = tree.lookupAll(".tree-cell").stream()
                .filter(TreeCell.class::isInstance)
                .map(node -> (TreeCell<?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTreeItem() == categoryItem)
                .findFirst()
                .orElseThrow();

        rightClickOn(categoryCell);
        ContextMenu contextMenu = categoryCell.getContextMenu();
        assertNotNull(contextMenu);
        MenuItem addLabelItem = contextMenu.getItems().stream()
                .filter(item -> "addLabelMenuItem".equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertFalse(addLabelItem.isDisable());
        interact(addLabelItem::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#labelNameField").tryQuery().isPresent());
        String labelName = "Added from tree " + System.nanoTime();
        interact(() -> {
            lookup("#labelNameField").<TextField>query().setText(labelName);
            lookup("#labelSaveButton").<Button>query().fire();
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> labelService().getAllLabels().stream().anyMatch(label -> labelName.equals(label.getName())));

        TreeItem<String> labelsRoot = treeItem(tree.getRoot(), I18n.get("classifications.labelsRoot"));
        assertTrue(labelsRoot.getChildren().stream().anyMatch(item -> labelName.equals(item.getValue())));
    }

    private TreeItem<String> treeItem(TreeItem<String> parent, String value) {
        return parent.getChildren().stream()
                .filter(item -> item.getValue().equals(value))
                .findFirst()
                .orElseThrow();
    }

    private TreeItem<String> categoryTreeItem(TreeView<String> tree, String name) {
        TreeItem<String> categoriesRoot = treeItem(tree.getRoot(), I18n.get("classifications.categoriesRoot"));
        return treeItem(categoriesRoot, name);
    }

    private void reloadClassificationsView() throws Exception {
        Stage stage = (Stage) lookup("#classificationTree").query().getScene().getWindow();
        Parent root = applicationContext.getBean(ViewLoader.class).load("classifications");
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
        interact(lookup("#pickerSave").<Button>query()::fire);
    }

    private CategoryService categoryService() {
        return applicationContext.getBean(CategoryService.class);
    }

    private LabelService labelService() {
        return applicationContext.getBean(LabelService.class);
    }
}
