package org.fintrax.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.CategoryService;
import org.fintrax.service.TransactionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoriesControllerTest extends AbstractUITest {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("categories");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void rightClickingRootOffersAddButNotEditAndShowsNoTransactions() {
        TreeView<String> tree = lookup("#categoryTree").query();
        TableView<Transaction> transactions = lookup("#categoryTransactionsTable").query();

        assertTrue(lookup("#addButton").queryAll().isEmpty());
        assertTrue(transactions.getItems().isEmpty());
        rightClickOn(treeCell(tree, I18n.get("categories.root")));

        assertFalse(lookup("Add Category").query().isDisabled());
        clickOn("Edit");
        assertTrue(lookup("#nameField").queryAll().isEmpty());
        clickOn("Delete");
        assertTrue(lookup("#confirmDeleteButton").queryAll().isEmpty());
        assertTrue(transactions.getItems().isEmpty());
    }

    @Test
    void addingFromRootCreatesTopLevelCategoryInPopup() {
        TreeView<String> tree = lookup("#categoryTree").query();
        rightClickOn(treeCell(tree, I18n.get("categories.root")));
        clickOn("Add Category");

        String name = "Top-level " + System.nanoTime();
        saveCategory(name);

        Category created = categoryService().getAllCategories().stream()
                .filter(category -> category.getName().equals(name))
                .findFirst()
                .orElseThrow();
        assertNull(created.getParentId());
    }

    @Test
    void contextMenuEditsNodeAndDoubleClickOpensEditor() {
        TreeView<String> tree = lookup("#categoryTree").query();
        String originalName = "Editable " + System.nanoTime();
        addCategoryAt(tree, tree.getRoot(), originalName);
        Long categoryId = findCategory(originalName).getId();

        rightClickOn(treeCell(tree, originalName));
        assertFalse(lookup("Add Category").query().isDisabled());
        assertFalse(lookup("Edit").query().isDisabled());
        clickOn("Edit");
        assertEquals(originalName, lookup("#nameField").<TextField>query().getText());
        assertTrue(lookup("#colorField").queryAll().isEmpty());
        assertTrue(lookup("#deleteButton").queryAll().isEmpty());
        assertTrue(lookup("Edit Category").queryAll().isEmpty());
        assertTrue(lookup("Depth:").queryAll().isEmpty());
        interact(lookup("#cancelButton").<Button>query()::fire);

        doubleClickOn(treeCell(tree, originalName));
        TextField nameField = lookup("#nameField").query();
        assertEquals(originalName, nameField.getText());
        String updatedName = "Updated " + System.nanoTime();
        interact(() -> nameField.setText(updatedName));
        interact(lookup("#saveButton").<Button>query()::fire);

        assertEquals(updatedName, categoryService().getCategory(categoryId).orElseThrow().getName());
    }

    @Test
    void deleteNodeMenuItemDeletesCategoryAfterConfirmation() {
        TreeView<String> tree = lookup("#categoryTree").query();
        String categoryName = "Delete me " + System.nanoTime();
        addCategoryAt(tree, tree.getRoot(), categoryName);
        Long categoryId = findCategory(categoryName).getId();

        rightClickOn(treeCell(tree, categoryName));
        clickOn("Delete");
        assertTrue(lookup("#moveChildrenCheckBox").queryAll().isEmpty());
        interact(lookup("#confirmDeleteButton").<Button>query()::fire);

        assertTrue(categoryService().getCategory(categoryId).isEmpty());
    }

    @Test
    void deletingCategoryDefaultsToDeletingItsEntireSubtree() {
        TreeView<String> tree = lookup("#categoryTree").query();
        String categoryName = "Delete subtree " + System.nanoTime();
        addCategoryAt(tree, tree.getRoot(), categoryName);
        TreeItem<String> categoryItem = treeItem(tree.getRoot(), categoryName);
        String childName = "Child " + System.nanoTime();
        addCategoryAt(tree, categoryItem, childName);
        TreeItem<String> childItem = treeItem(treeItem(tree.getRoot(), categoryName), childName);
        String grandchildName = "Grandchild " + System.nanoTime();
        addCategoryAt(tree, childItem, grandchildName);
        Long categoryId = findCategory(categoryName).getId();
        Long childId = findCategory(childName).getId();
        Long grandchildId = findCategory(grandchildName).getId();

        rightClickOn(treeCell(tree, categoryName));
        clickOn("Delete");
        CheckBox moveChildren = lookup("#moveChildrenCheckBox").query();
        assertFalse(moveChildren.isSelected());
        interact(lookup("#confirmDeleteButton").<Button>query()::fire);

        assertTrue(categoryService().getCategory(categoryId).isEmpty());
        assertTrue(categoryService().getCategory(childId).isEmpty());
        assertTrue(categoryService().getCategory(grandchildId).isEmpty());
    }

    @Test
    void selectingMoveChildrenPromotesThemToDeletedCategoriesParent() {
        TreeView<String> tree = lookup("#categoryTree").query();
        String parentName = "Parent " + System.nanoTime();
        addCategoryAt(tree, tree.getRoot(), parentName);
        TreeItem<String> parentItem = treeItem(tree.getRoot(), parentName);
        String categoryName = "Delete and promote " + System.nanoTime();
        addCategoryAt(tree, parentItem, categoryName);
        TreeItem<String> categoryItem = treeItem(treeItem(tree.getRoot(), parentName), categoryName);
        String childName = "Promoted child " + System.nanoTime();
        addCategoryAt(tree, categoryItem, childName);
        Long parentId = findCategory(parentName).getId();
        Long categoryId = findCategory(categoryName).getId();
        Long childId = findCategory(childName).getId();

        rightClickOn(treeCell(tree, categoryName));
        clickOn("Delete");
        CheckBox moveChildren = lookup("#moveChildrenCheckBox").query();
        assertEquals(I18n.get("categories.delete.moveChildrenToParent", parentName), moveChildren.getText());
        assertFalse(moveChildren.isSelected());
        interact(moveChildren::fire);
        interact(lookup("#confirmDeleteButton").<Button>query()::fire);

        assertTrue(categoryService().getCategory(categoryId).isEmpty());
        assertEquals(parentId, categoryService().getCategory(childId).orElseThrow().getParentId());
    }

    @Test
    void movingChildrenOfTopLevelCategoryPromotesThemToTopLevel() {
        TreeView<String> tree = lookup("#categoryTree").query();
        String categoryName = "Top-level to delete " + System.nanoTime();
        addCategoryAt(tree, tree.getRoot(), categoryName);
        TreeItem<String> categoryItem = treeItem(tree.getRoot(), categoryName);
        String childName = "Top-level child " + System.nanoTime();
        addCategoryAt(tree, categoryItem, childName);
        Long categoryId = findCategory(categoryName).getId();
        Long childId = findCategory(childName).getId();

        rightClickOn(treeCell(tree, categoryName));
        clickOn("Delete");
        CheckBox moveChildren = lookup("#moveChildrenCheckBox").query();
        assertEquals(I18n.get("categories.delete.moveChildrenToParent",
                I18n.get("categories.delete.topLevel")), moveChildren.getText());
        interact(moveChildren::fire);
        interact(lookup("#confirmDeleteButton").<Button>query()::fire);

        assertTrue(categoryService().getCategory(categoryId).isEmpty());
        assertNull(categoryService().getCategory(childId).orElseThrow().getParentId());
    }

    @Test
    void addingChildExpandsItsAncestorsAndMakesItVisible() {
        TreeView<String> tree = lookup("#categoryTree").query();
        String ancestorName = "Ancestor " + System.nanoTime();
        addCategoryAt(tree, tree.getRoot(), ancestorName);

        TreeItem<String> ancestorItem = treeItem(tree.getRoot(), ancestorName);
        interact(() -> ancestorItem.setExpanded(true));
        String parentName = "Parent " + System.nanoTime();
        addCategoryAt(tree, ancestorItem, parentName);

        TreeItem<String> refreshedAncestor = treeItem(tree.getRoot(), ancestorName);
        TreeItem<String> parentItem = treeItem(refreshedAncestor, parentName);
        String childName = "Child " + System.nanoTime();
        addCategoryAt(tree, parentItem, childName);

        TreeItem<String> finalAncestor = treeItem(tree.getRoot(), ancestorName);
        TreeItem<String> finalParent = treeItem(finalAncestor, parentName);
        TreeItem<String> childItem = treeItem(finalParent, childName);
        Category parent = findCategory(parentName);
        Category child = findCategory(childName);

        assertEquals(parent.getId(), child.getParentId());
        assertTrue(finalAncestor.isExpanded());
        assertTrue(finalParent.isExpanded());
        assertTrue(tree.getRow(childItem) >= 0);
    }

    @Test
    void addCategoryIsUnavailableAtMaximumDepth() {
        TreeView<String> tree = lookup("#categoryTree").query();
        List<String> path = new ArrayList<>();
        TreeItem<String> parent = tree.getRoot();

        for (int depth = 1; depth <= 5; depth++) {
            String name = "Level " + depth + " " + System.nanoTime();
            addCategoryAt(tree, parent, name);
            path.add(name);
            parent = treeItemAtPath(tree.getRoot(), path);
        }

        rightClickOn(treeCell(tree, parent.getValue()));
        clickOn("Add Category");

        assertTrue(lookup("#nameField").queryAll().isEmpty());
    }

    @Test
    void selectedCategoryShowsTransactionsFromItsSubtreeAndColumnsSortRows() {
        TreeView<String> tree = lookup("#categoryTree").query();
        String categoryName = "Transactions " + System.nanoTime();
        addCategoryAt(tree, tree.getRoot(), categoryName);
        Category category = findCategory(categoryName);
        Category childCategory = categoryService().createCategory("Child " + System.nanoTime(), category.getId(), null);
        Category grandchildCategory = categoryService().createCategory(
                "Grandchild " + System.nanoTime(), childCategory.getId(), null);
        Category otherCategory = categoryService().createCategory("Other " + System.nanoTime(), null, null);

        Transaction larger = createTransaction(category.getId(), "Larger", "20", LocalDate.of(2025, 2, 1));
        Transaction smaller = createTransaction(category.getId(), "Smaller", "-5", LocalDate.of(2025, 1, 1));
        Transaction childTransaction = createTransaction(
                childCategory.getId(), "Child transaction", "7", LocalDate.of(2025, 2, 2));
        Transaction grandchildTransaction = createTransaction(
                grandchildCategory.getId(), "Grandchild transaction", "2", LocalDate.of(2025, 2, 3));
        createTransaction(otherCategory.getId(), "Unrelated", "100", LocalDate.of(2025, 3, 1));

        interact(() -> tree.getSelectionModel().select(treeItem(tree.getRoot(), categoryName)));
        TableView<Transaction> table = lookup("#categoryTransactionsTable").query();
        TableColumn<Transaction, ?> amountColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.amount").equals(column.getText()))
                .findFirst()
                .orElseThrow();

        assertEquals(4, table.getItems().size());
        assertTrue(table.getItems().stream().allMatch(tx -> Set.of(category.getId(), childCategory.getId(),
                grandchildCategory.getId()).contains(tx.getCategoryId())));
        assertTrue(table.getColumns().stream().allMatch(column -> column.isSortable()));
        assertFalse(table.getColumns().stream()
                .anyMatch(column -> I18n.get("col.balance").equals(column.getText())));
        TableColumn<Transaction, ?> transactionCategoryColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.category").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        assertEquals(category.getName(), transactionCategoryColumn.getCellData(table.getItems().indexOf(larger)));
        assertEquals(category.getName(), transactionCategoryColumn.getCellData(table.getItems().indexOf(smaller)));
        assertEquals(childCategory.getName(),
                transactionCategoryColumn.getCellData(table.getItems().indexOf(childTransaction)));
        assertEquals(grandchildCategory.getName(),
                transactionCategoryColumn.getCellData(table.getItems().indexOf(grandchildTransaction)));

        interact(() -> {
            amountColumn.setSortType(TableColumn.SortType.ASCENDING);
            table.getSortOrder().setAll(amountColumn);
            table.sort();
        });
        assertEquals(smaller.getId(), table.getItems().get(0).getId());

        interact(() -> tree.getSelectionModel().select(tree.getRoot()));
        assertTrue(table.getItems().isEmpty());
    }

    private TreeCell<?> treeCell(TreeView<String> tree, String value) {
        return tree.lookupAll(".tree-cell").stream()
                .filter(node -> node instanceof TreeCell<?>)
                .map(node -> (TreeCell<?>) node)
                .filter(cell -> value.equals(cell.getText()))
                .findFirst()
                .orElseThrow();
    }

    private TreeItem<String> treeItem(TreeItem<String> parent, String value) {
        return parent.getChildren().stream()
                .filter(item -> item.getValue().equals(value))
                .findFirst()
                .orElseThrow();
    }

    private TreeItem<String> treeItemAtPath(TreeItem<String> root, List<String> path) {
        TreeItem<String> current = root;
        for (String value : path) {
            current = treeItem(current, value);
        }
        return current;
    }

    private void saveCategory(String name) {
        TextField nameField = lookup("#nameField").query();
        interact(() -> nameField.setText(name));
        interact(lookup("#saveButton").<Button>query()::fire);
    }

    private void addCategoryAt(TreeView<String> tree, TreeItem<String> item, String name) {
        rightClickOn(treeCell(tree, item.getValue()));
        clickOn("Add Category");
        saveCategory(name);
    }

    private Category findCategory(String name) {
        return categoryService().getAllCategories().stream()
                .filter(category -> category.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private Transaction createTransaction(Long categoryId, String payee, String amount, LocalDate bookingDate) {
        return applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                .categoryId(categoryId)
                .originalPayee(payee)
                .amount(new BigDecimal(amount))
                .bookingDate(bookingDate)
                .build());
    }

    private CategoryService categoryService() {
        return applicationContext.getBean(CategoryService.class);
    }
}
