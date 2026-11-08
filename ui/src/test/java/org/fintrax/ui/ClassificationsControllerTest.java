package org.fintrax.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Category;
import org.fintrax.model.Label;
import org.fintrax.model.Transaction;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.TransactionService;
import org.fintrax.ui.control.LabelChipFactory;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationsControllerTest extends AbstractUITest {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("classifications");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void transactionFiltersStartCollapsedInClassificationsView() {
        assertFalse(lookup("#additionalFilters").query().isVisible());
        assertTrue(lookup("#toggleAdditionalFiltersButton").query().isVisible());
        assertTrue(lookup("#searchField").query().isVisible());
        assertTrue(lookup("#datePresetFilter").query().isVisible());
    }

    @Test
    void classificationTransactionFiltersCanBeExpandedAndCollapsedWithSummary() {
        Button toggleButton = lookup("#toggleAdditionalFiltersButton").query();

        interact(toggleButton::fire);
        assertTrue(lookup("#additionalFilters").query().isVisible());
        interact(() -> lookup("#minAmountField").<TextField>query().setText("25.00"));
        interact(toggleButton::fire);

        assertFalse(lookup("#additionalFilters").query().isVisible());
        TextField summary = lookup("#collapsedFilterSummary").query();
        assertTrue(summary.isVisible());
        assertEquals(I18n.get("filter.summary.item", I18n.get("filter.minAmount"), "25.00"), summary.getText());
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
    void categoryEditorMovesCategoryToSelectedParentAndOffersTopLevel() throws Exception {
        Category oldParent = categoryService().createCategory("Old parent " + System.nanoTime(), null, null);
        Category newParent = categoryService().createCategory("New parent " + System.nanoTime(), null, null);
        Category category = categoryService().createCategory("Movable " + System.nanoTime(), oldParent.getId(), null);
        Category child = categoryService().createCategory("Child " + System.nanoTime(), category.getId(), null);
        try {
            reloadClassificationsView();
            TreeView<String> tree = lookup("#classificationTree").query();
            TreeItem<String> oldParentItem = categoryTreeItem(tree, oldParent.getName());
            TreeItem<String> categoryItem = treeItem(oldParentItem, category.getName());
            interact(() -> oldParentItem.setExpanded(true));
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> hasTreeCell(tree, categoryItem));
            TreeCell<?> categoryCell = treeCellFor(tree, categoryItem);

            openCategoryEditor(tree, categoryItem, categoryCell);
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                    () -> lookup("#parentCategoryCombo").tryQuery().isPresent());
            ComboBox<?> parentSelector = lookup("#parentCategoryCombo").query();
            assertTrue(hasParentChoice(parentSelector, I18n.get("classifications.categoriesRoot")));
            selectParent(parentSelector, newParent.getName());
            interact(() -> lookup("#saveButton").<Button>query().fire());
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                    () -> newParent.getId().equals(categoryService().getCategory(category.getId())
                            .orElseThrow().getParentId()));

            assertEquals(category.getId(), categoryService().getCategory(child.getId()).orElseThrow().getParentId());
            TreeView<String> updatedTree = lookup("#classificationTree").query();
            TreeItem<String> newParentItem = categoryTreeItem(updatedTree, newParent.getName());
            assertEquals(category.getName(), treeItem(newParentItem, category.getName()).getValue());
        } finally {
            categoryService().deleteCategory(oldParent.getId(), null, false);
            categoryService().deleteCategory(newParent.getId(), null, false);
        }
    }

    @Test
    void categoryEditorCanMoveCategoryToTopLevel() throws Exception {
        Category parent = categoryService().createCategory("Parent " + System.nanoTime(), null, null);
        Category category = categoryService().createCategory("Movable " + System.nanoTime(), parent.getId(), null);
        try {
            reloadClassificationsView();
            TreeView<String> tree = lookup("#classificationTree").query();
            TreeItem<String> parentItem = categoryTreeItem(tree, parent.getName());
            interact(() -> parentItem.setExpanded(true));
            TreeItem<String> categoryItem = treeItem(parentItem, category.getName());
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> hasTreeCell(tree, categoryItem));
            TreeCell<?> categoryCell = treeCellFor(tree, categoryItem);

            openCategoryEditor(tree, categoryItem, categoryCell);
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                    () -> lookup("#parentCategoryCombo").tryQuery().isPresent());
            selectParent(lookup("#parentCategoryCombo").query(), I18n.get("classifications.categoriesRoot"));
            interact(() -> lookup("#saveButton").<Button>query().fire());
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                    () -> categoryService().getCategory(category.getId()).orElseThrow().getParentId() == null);

            assertNotNull(categoryTreeItem(lookup("#classificationTree").query(), category.getName()));
        } finally {
            categoryService().deleteCategory(parent.getId(), null, false);
        }
    }

    @Test
    void classificationTableShowsAccountNameColumn() throws Exception {
        BankAccount account = applicationContext.getBean(AccountService.class)
                .createAccount("DE89370400440532013000", null, "Detail test bank", "Test holder", null, null);
        Category category = categoryService().createCategory("Account column " + System.nanoTime(), null, null);
        Transaction transaction = applicationContext.getBean(TransactionService.class).createTransaction(
                Transaction.builder()
                        .categoryId(category.getId())
                        .accountId(account.getId())
                        .originalPayee("Account column transaction")
                        .amount(new BigDecimal("10"))
                        .bookingDate(LocalDate.of(2025, 2, 1))
                        .build());

        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, category.getName())));
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
        TableColumn<?, ?> accountColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.account").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        TableCell<?, ?> accountCell = table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == accountColumn
                        && cell.getTableRow() != null
                        && cell.getTableRow().getItem() instanceof Transaction rowTransaction
                        && transaction.getId().equals(rowTransaction.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals("Detail test bank (DE89****3000)", accountCell.getText());
    }

    @Test
    void classificationSortOrderIsSharedAcrossCategoriesAndViewReloads() throws Exception {
        String sortSettingKey = "classification.transaction.columns.sort";
        SettingsService settingsService = applicationContext.getBean(SettingsService.class);
        String previousSortOrder = settingsService.getSettingValue(sortSettingKey);
        Category firstCategory = categoryService().createCategory(
                "First sort category " + System.nanoTime(), null, null);
        Category secondCategory = categoryService().createCategory(
                "Second sort category " + System.nanoTime(), null, null);
        Transaction olderTransaction = createTransaction(secondCategory.getId(), "Older transaction", "10",
                LocalDate.of(2025, 2, 1));
        Transaction newerTransaction = createTransaction(secondCategory.getId(), "Newer transaction", "20",
                LocalDate.of(2025, 2, 2));
        createTransaction(firstCategory.getId(), "First category transaction", "30",
                LocalDate.of(2025, 2, 3));

        try {
            reloadClassificationsView();
            TreeView<String> tree = lookup("#classificationTree").query();
            TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
            interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, firstCategory.getName())));

            TableColumn<Transaction, ?> dateColumn = table.getColumns().stream()
                    .filter(column -> I18n.get("col.bookingDate").equals(column.getText()))
                    .findFirst()
                    .orElseThrow();
            interact(() -> {
                dateColumn.setSortType(TableColumn.SortType.DESCENDING);
                table.getSortOrder().setAll(dateColumn);
            });
            assertEquals("date=DESCENDING", settingsService.getSettingValue(sortSettingKey));

            interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, secondCategory.getName())));
            assertSame(dateColumn, table.getSortOrder().getFirst());
            assertEquals("date=DESCENDING", settingsService.getSettingValue(sortSettingKey));
            assertEquals(List.of(newerTransaction.getId(), olderTransaction.getId()),
                    table.getItems().stream().map(Transaction::getId).toList());

            reloadClassificationsView();
            TreeView<String> restoredTree = lookup("#classificationTree").query();
            TableView<Transaction> restoredTable = lookup("#classificationTransactionsTable").query();
            assertEquals(1, restoredTable.getSortOrder().size());
            assertEquals(TableColumn.SortType.DESCENDING, restoredTable.getSortOrder().getFirst().getSortType());
            assertEquals("date=DESCENDING", settingsService.getSettingValue(sortSettingKey));

            interact(() -> restoredTree.getSelectionModel().select(
                    categoryTreeItem(restoredTree, secondCategory.getName())));
            assertEquals(List.of(newerTransaction.getId(), olderTransaction.getId()),
                    restoredTable.getItems().stream().map(Transaction::getId).toList());
        } finally {
            settingsService.saveSettingValue(sortSettingKey, previousSortOrder == null ? "" : previousSortOrder);
        }
    }

    @Test
    void classificationTransactionDateColumnsUseCurrentLocaleShortDateFormat() throws Exception {
        Category category = categoryService().createCategory("Localized date " + System.nanoTime(), null, null);
        LocalDate bookingDate = LocalDate.of(2025, 2, 1);
        LocalDate valueDate = bookingDate.plusDays(3);
        Transaction transaction = createTransaction(category.getId(), "Localized date transaction", "20",
                bookingDate, valueDate);

        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, category.getName())));
        WaitForAsyncUtils.waitForFxEvents();

        TableColumn<?, ?> bookingDateColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.bookingDate").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        TableColumn<?, ?> valueDateColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.valueDate").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());

        assertEquals(dateFormatter.format(bookingDate),
                cellForTransaction(table, bookingDateColumn, transaction).getText());
        assertEquals(dateFormatter.format(valueDate),
                cellForTransaction(table, valueDateColumn, transaction).getText());
    }

    @Test
    void transactionFiltersNarrowCategorySubtreeWithoutEscapingIt() throws Exception {
        Category category = categoryService().createCategory("Filter parent " + System.nanoTime(), null, null);
        Category childCategory = categoryService().createCategory(
                "Filter child " + System.nanoTime(), category.getId(), null);
        Category otherCategory = categoryService().createCategory("Filter other " + System.nanoTime(), null, null);
        Transaction parentMatch = createTransaction(category.getId(), "Family vacation", "20",
                LocalDate.of(2025, 2, 1));
        Transaction childMatch = createTransaction(childCategory.getId(), "Family ticket", "7",
                LocalDate.of(2025, 2, 2));
        createTransaction(childCategory.getId(), "Child groceries", "30", LocalDate.of(2025, 2, 3));
        Transaction otherMatch = createTransaction(otherCategory.getId(), "Family elsewhere", "40",
                LocalDate.of(2025, 2, 4));

        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
        ComboBox<String> categoryFilter = lookup("#categoryFilter").query();
        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, category.getName())));

        interact(() -> lookup("#searchField").<TextField>query().setText("family"));

        assertEquals(List.of(parentMatch.getId(), childMatch.getId()),
                table.getItems().stream().map(Transaction::getId).toList());

        interact(() -> categoryFilter.getSelectionModel().select(childCategory.getName()));

        assertEquals(List.of(childMatch.getId()), table.getItems().stream().map(Transaction::getId).toList());

        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, otherCategory.getName())));

        assertTrue(table.getItems().isEmpty());

        interact(lookup("#clearFiltersButton").<Button>query()::fire);

        assertEquals(List.of(otherMatch.getId()), table.getItems().stream().map(Transaction::getId).toList());
    }

    @Test
    void transactionCategoryFilterOptionsAreSortedAlphabetically() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        Category zulu = categoryService().createCategory("Zulu sort " + suffix, null, null);
        Category alpha = categoryService().createCategory("Alpha sort " + suffix, null, null);

        reloadClassificationsView();
        ComboBox<String> categoryFilter = lookup("#categoryFilter").query();

        interact(() -> {
            assertEquals(I18n.get("filter.allCategories"), categoryFilter.getItems().get(0));
            assertTrue(categoryFilter.getItems().indexOf(alpha.getName())
                    < categoryFilter.getItems().indexOf(zulu.getName()));
        });
    }

    @Test
    void classificationTransactionFooterShowsSumOfFilteredAmounts() throws Exception {
        Category category = categoryService().createCategory("Amount total " + System.nanoTime(), null, null);
        Category otherCategory = categoryService().createCategory("Other total " + System.nanoTime(), null, null);
        createTransaction(category.getId(), "Income", "25", LocalDate.of(2025, 2, 1));
        createTransaction(category.getId(), "Expense", "-8", LocalDate.of(2025, 2, 2));
        createTransaction(otherCategory.getId(), "Unrelated", "100", LocalDate.of(2025, 2, 3));

        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, category.getName())));
        javafx.scene.control.Label total = lookup("#classificationTransactionsTotalAmount").query();

        assertEquals(String.format("%,.2f EUR", new BigDecimal("17")), total.getText());

        interact(() -> lookup("#searchField").<TextField>query().setText("Expense"));

        assertEquals(String.format("%,.2f EUR", new BigDecimal("-8")), total.getText());
    }

    @Test
    void transactionFiltersNarrowSelectedLabelTransactions() throws Exception {
        Label label = labelService().createLabel("Filtered label " + System.nanoTime(), "#1976d2", null);
        Label otherLabel = labelService().createLabel("Other filtered label " + System.nanoTime(), "#cc4400", null);
        Category category = categoryService().createCategory("Label filter " + System.nanoTime(), null, null);
        Transaction matching = createTransaction(category.getId(), "Travel receipt", "20",
                LocalDate.of(2025, 2, 1));
        Transaction sameSearchOtherLabel = createTransaction(category.getId(), "Travel reimbursement", "30",
                LocalDate.of(2025, 2, 2));
        applicationContext.getBean(TransactionService.class).updateLabels(matching.getId(), Set.of(label.getId()));
        applicationContext.getBean(TransactionService.class).updateLabels(
                sameSearchOtherLabel.getId(), Set.of(otherLabel.getId()));

        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
        interact(() -> tree.getSelectionModel().select(
                treeItem(treeItem(tree.getRoot(), I18n.get("classifications.labelsRoot")), label.getName())));

        interact(() -> lookup("#searchField").<TextField>query().setText("travel"));

        assertEquals(List.of(matching.getId()), table.getItems().stream().map(Transaction::getId).toList());
    }

    @Test
    void invalidDateKeepsCurrentResultsButClearsWhenTreeScopeChanges() throws Exception {
        Category selectedCategory = categoryService().createCategory("Invalid date selected " + System.nanoTime(),
                null, null);
        Category otherCategory = categoryService().createCategory("Invalid date other " + System.nanoTime(), null,
                null);
        Transaction selectedTransaction = createTransaction(selectedCategory.getId(), "Selected scope", "20",
                LocalDate.of(2025, 2, 1));
        createTransaction(otherCategory.getId(), "Other scope", "30", LocalDate.of(2025, 2, 2));

        reloadClassificationsView();
        TreeView<String> tree = lookup("#classificationTree").query();
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
        DatePicker fromDate = lookup("#fromDateFilter").query();
        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, selectedCategory.getName())));

        interact(() -> fromDate.getEditor().setText("not a date"));

        assertEquals(List.of(selectedTransaction.getId()), table.getItems().stream().map(Transaction::getId).toList());
        assertTrue(fromDate.getEditor().getStyleClass().contains("invalid-date"));

        interact(() -> tree.getSelectionModel().select(categoryTreeItem(tree, otherCategory.getName())));

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
    void classificationTransactionColumnsCanBeShownAndHidden() throws Exception {
        MenuButton columnMenu = lookup("#columnMenuButton").query();
        TableView<Transaction> table = lookup("#classificationTransactionsTable").query();
        Stage stage = (Stage) table.getScene().getWindow();
        double originalWindowWidth = stage.getWidth();
        interact(() -> stage.setWidth(Math.max(originalWindowWidth, 1280)));
        WaitForAsyncUtils.waitForFxEvents();

        assertSame(columnMenu, table.getColumns().getLast().getGraphic());
        double totalVisibleColumnWidth = table.getColumns().stream()
                .filter(TableColumn::isVisible)
                .mapToDouble(TableColumn::getWidth)
                .sum();
        assertTrue(totalVisibleColumnWidth <= table.getWidth() + 1,
                "all transaction columns should fit in the widened test window");
        List<CheckMenuItem> columnItems = columnMenu.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .toList();
        assertEquals(List.of("col.bookingDate", "col.valueDate", "col.payee", "col.account", "col.purpose",
                        "col.amount", "col.category", "col.labels").stream().map(I18n::get).toList(),
                columnItems.stream().map(MenuItem::getText).toList());
        assertTrue(columnItems.stream().allMatch(CheckMenuItem::isSelected));
        assertEquals(I18n.get("button.columns.reset"), columnMenu.getItems().getLast().getText());

        var initialColumnOrder = List.copyOf(table.getColumns());
        Parent originalRoot = table.getScene().getRoot();
        CheckMenuItem purposeItem = columnItems.stream()
                .filter(item -> I18n.get("col.purpose").equals(item.getText()))
                .findFirst().orElseThrow();
        try {
            interact(() -> purposeItem.setSelected(false));

            assertFalse(table.getColumns().stream()
                    .filter(column -> I18n.get("col.purpose").equals(column.getText()))
                    .findFirst().orElseThrow().isVisible());
            assertFalse(purposeItem.isSelected());
            assertTrue(applicationContext.getBean(SettingsService.class)
                    .getSettingValue("classification.transaction.columns").contains("purpose=false"));

            interact(() -> {
                var dateColumn = table.getColumns().remove(0);
                table.getColumns().add(1, dateColumn);
            });
            assertEquals("valueDate,date,payee,account,purpose,amount,category,labels",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("classification.transaction.columns.order"));

            Parent restoredView = applicationContext.getBean(ViewLoader.class).load("classifications");
            interact(() -> stage.getScene().setRoot(restoredView));
            TableView<?> restoredTable = lookup("#classificationTransactionsTable").query();
            assertEquals(List.of("col.valueDate", "col.bookingDate", "col.payee", "col.account", "col.purpose",
                            "col.amount", "col.category", "col.labels").stream().map(I18n::get).toList(),
                    restoredTable.getColumns().stream().limit(8).map(TableColumn::getText).toList());
            assertFalse(restoredTable.getColumns().stream()
                    .filter(column -> I18n.get("col.purpose").equals(column.getText()))
                    .findFirst().orElseThrow().isVisible());

            MenuButton restoredMenu = lookup("#columnMenuButton").query();
            interact(restoredMenu.getItems().getLast()::fire);
            assertTrue(restoredTable.getColumns().stream().limit(8)
                    .allMatch(TableColumn::isVisible));
            assertEquals("date=true,valueDate=true,payee=true,account=true,purpose=true,amount=true,category=true,labels=true",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("classification.transaction.columns"));
            assertEquals("date,valueDate,payee,account,purpose,amount,category,labels",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("classification.transaction.columns.order"));
        } finally {
            interact(() -> {
                purposeItem.setSelected(true);
                table.getColumns().setAll(initialColumnOrder);
                stage.getScene().setRoot(originalRoot);
                stage.setWidth(originalWindowWidth);
            });
        }
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
        assertTrue(labelsCell.getGraphic() instanceof HBox);
        HBox labelChips = (HBox) labelsCell.getGraphic();
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

    @Test
    void labelChipStripShowsEllipsisWhenColumnIsTooNarrow() {
        List<Label> labels = List.of(
                labelService().createLabel("Long travel label one", "#1976d2", null),
                labelService().createLabel("Long travel label two", "#cc4400", null),
                labelService().createLabel("Long travel label three", "#669900", null));
        HBox chips = LabelChipFactory.createAll(labels);

        interact(() -> {
            new Scene(chips, 100, 30);
            chips.applyCss();
            chips.resize(100, 30);
            chips.requestLayout();
            chips.layout();
        });

        long visibleLabelCount = chips.getChildren().stream()
                .filter(javafx.scene.control.Label.class::isInstance)
                .map(javafx.scene.control.Label.class::cast)
                .filter(javafx.scene.control.Label::isVisible)
                .filter(chip -> !"...".equals(chip.getText()))
                .count();
        double chipFontSize = chips.getChildren().stream()
                .filter(javafx.scene.control.Label.class::isInstance)
                .map(javafx.scene.control.Label.class::cast)
                .filter(chip -> !"...".equals(chip.getText()))
                .findFirst()
                .orElseThrow()
                .getFont()
                .getSize();
        assertTrue(visibleLabelCount < labels.size(),
                "visible labels=" + visibleLabelCount + ", strip width=" + chips.getWidth()
                        + ", child widths=" + chips.getChildren().stream()
                        .map(node -> node.prefWidth(-1)).toList());
        assertTrue(chips.getChildren().stream()
                .filter(javafx.scene.control.Label.class::isInstance)
                .map(javafx.scene.control.Label.class::cast)
                .anyMatch(chip -> "...".equals(chip.getText()) && chip.isVisible()
                        && chip.getFont().getSize() > chipFontSize));
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

    private boolean hasTreeCell(TreeView<?> tree, TreeItem<?> item) {
        return tree.lookupAll(".tree-cell").stream()
                .filter(TreeCell.class::isInstance)
                .map(TreeCell.class::cast)
                .anyMatch(cell -> !cell.isEmpty() && cell.getTreeItem() == item);
    }

    private TreeCell<?> treeCellFor(TreeView<?> tree, TreeItem<?> item) {
        return tree.lookupAll(".tree-cell").stream()
                .filter(TreeCell.class::isInstance)
                .map(TreeCell.class::cast)
                .filter(cell -> !cell.isEmpty() && cell.getTreeItem() == item)
                .findFirst()
                .orElseThrow();
    }

    private void openCategoryEditor(TreeView<String> tree, TreeItem<String> item, TreeCell<?> cell) {
        interact(() -> tree.getSelectionModel().select(item));
        MenuItem editItem = cell.getContextMenu().getItems().stream()
                .filter(menuItem -> "editNodeMenuItem".equals(menuItem.getId()))
                .findFirst()
                .orElseThrow();
        interact(editItem::fire);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void selectParent(ComboBox<?> selector, String label) {
        ComboBox rawSelector = selector;
        Object choice = (Object) rawSelector.getItems().stream()
                .filter(item -> label.equals(rawSelector.getConverter().toString(item)))
                .findFirst()
                .orElseThrow();
        interact(() -> rawSelector.getSelectionModel().select(choice));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean hasParentChoice(ComboBox<?> selector, String label) {
        ComboBox rawSelector = selector;
        return rawSelector.getItems().stream()
                .anyMatch(choice -> label.equals(rawSelector.getConverter().toString(choice)));
    }

    private void reloadClassificationsView() throws Exception {
        Stage stage = (Stage) lookup("#classificationTree").query().getScene().getWindow();
        Parent root = applicationContext.getBean(ViewLoader.class).load("classifications");
        interact(() -> stage.getScene().setRoot(root));
    }

    private Transaction createTransaction(Long categoryId, String payee, String amount, LocalDate bookingDate) {
        return createTransaction(categoryId, payee, amount, bookingDate, null);
    }

    private Transaction createTransaction(Long categoryId, String payee, String amount,
                                          LocalDate bookingDate, LocalDate valueDate) {
        return applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                .categoryId(categoryId)
                .originalPayee(payee)
                .amount(new BigDecimal(amount))
                .bookingDate(bookingDate)
                .valueDate(valueDate)
                .build());
    }

    private TableCell<?, ?> cellForTransaction(TableView<Transaction> table, TableColumn<?, ?> column,
                                               Transaction transaction) {
        return table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == column
                        && cell.getTableRow() != null && cell.getTableRow().getItem() instanceof Transaction row
                        && transaction.getId().equals(row.getId()))
                .findFirst()
                .orElseThrow();
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
