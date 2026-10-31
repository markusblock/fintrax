package org.fintrax.ui;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionsControllerTest extends AbstractUITest {
    @Override
    public void start(Stage stage) throws Exception {
        Category category = applicationContext.getBean(CategoryService.class)
                .createCategory("Cell category " + System.nanoTime(), null, null);
        org.fintrax.model.Label label = applicationContext.getBean(LabelService.class)
                .createLabel("Cell label " + System.nanoTime(), "#3B82F6", null);
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
    void transactionLabelsShowAsColoredChips() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        Transaction transaction = table.getItems().stream()
                .filter(item -> item.getLabelIds() != null && !item.getLabelIds().isEmpty())
                .findFirst()
                .orElseThrow();
        Long labelId = transaction.getLabelIds().iterator().next();
        org.fintrax.model.Label label = applicationContext.getBean(LabelService.class)
                .getLabel(labelId).orElseThrow();
        TableColumn<?, ?> labelsColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.labels").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        TableCell<?, ?> labelsCell = table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == labelsColumn
                        && cell.getTableRow() != null
                        && cell.getTableRow().getItem() instanceof Transaction rowTransaction
                        && transaction.getId().equals(rowTransaction.getId()))
                .findFirst()
                .orElseThrow();

        assertTrue(labelsCell.getGraphic() instanceof HBox);
        javafx.scene.control.Label chip = (javafx.scene.control.Label)
                ((HBox) labelsCell.getGraphic()).getChildren().getFirst();
        assertEquals(label.getName(), chip.getText());
        assertEquals(javafx.scene.paint.Color.web(label.getColor()),
                chip.getBackground().getFills().getFirst().getFill());
    }

    @Test
    void datePresetFillsCalendarPeriodBounds() {
        ComboBox<?> presets = lookup("#datePresetFilter").query();
        DatePicker fromDate = lookup("#fromDateFilter").query();
        DatePicker toDate = lookup("#toDateFilter").query();
        LocalDate today = LocalDate.now();

        interact(() -> presets.getSelectionModel().select(3));

        assertEquals(LocalDate.of(today.getYear() - 1, 1, 1), fromDate.getValue());
        assertEquals(LocalDate.of(today.getYear() - 1, 12, 31), toDate.getValue());
    }

    @Test
    void applyingTypedDateRangeFiltersTransactions() {
        LocalDate matchingDate = LocalDate.of(2024, 2, 10);
        Transaction matching = applicationContext.getBean(TransactionService.class).createTransaction(
                Transaction.builder().originalPayee("date-filter-match-" + System.nanoTime())
                        .bookingDate(matchingDate).amount(BigDecimal.ONE).build());
        Transaction outsideRange = applicationContext.getBean(TransactionService.class).createTransaction(
                Transaction.builder().originalPayee("date-filter-outside-" + System.nanoTime())
                        .bookingDate(matchingDate.plusDays(1)).amount(BigDecimal.ONE).build());
        DatePicker fromDate = lookup("#fromDateFilter").query();
        DatePicker toDate = lookup("#toDateFilter").query();
        TableView<Transaction> table = lookup("#transactionTable").query();
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());

        interact(() -> {
            fromDate.getEditor().setText(formatter.format(matchingDate));
            toDate.getEditor().setText(formatter.format(matchingDate));
        });

        assertTrue(table.getItems().stream().anyMatch(tx -> tx.getId().equals(matching.getId())));
        assertFalse(table.getItems().stream().anyMatch(tx -> tx.getId().equals(outsideRange.getId())));
    }

    @Test
    void invalidDateBlocksFilteringAndMarksDateField() {
        DatePicker fromDate = lookup("#fromDateFilter").query();
        TableView<Transaction> table = lookup("#transactionTable").query();
        List<Long> visibleIds = table.getItems().stream().map(Transaction::getId).toList();

        interact(() -> {
            fromDate.getEditor().setText("not a date");
        });

        assertEquals(visibleIds, table.getItems().stream().map(Transaction::getId).toList());
        assertTrue(fromDate.getEditor().getStyleClass().contains("invalid-date"));
    }

    @Test
    void reversedDateRangeMarksBothDateFieldsAndBlocksFiltering() {
        DatePicker fromDate = lookup("#fromDateFilter").query();
        DatePicker toDate = lookup("#toDateFilter").query();
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());

        interact(() -> {
            fromDate.getEditor().setText(formatter.format(LocalDate.of(2024, 3, 2)));
            toDate.getEditor().setText(formatter.format(LocalDate.of(2024, 3, 1)));
        });

        assertTrue(fromDate.getEditor().getStyleClass().contains("invalid-date"));
        assertTrue(toDate.getEditor().getStyleClass().contains("invalid-date"));
    }

    @Test
    void changingFiltersImmediatelyUpdatesTransactionsAndClearResetsEveryFilter() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        ComboBox<String> categoryFilter = lookup("#categoryFilter").query();
        TextField searchField = lookup("#searchField").query();
        TextField minAmountField = lookup("#minAmountField").query();
        String categoryName = categoryFilter.getItems().get(1);
        Long categoryId = applicationContext.getBean(CategoryService.class).getAllCategories().stream()
                .filter(category -> category.getName().equals(categoryName))
                .map(Category::getId).findFirst().orElseThrow();
        Transaction matching = table.getItems().stream()
                .filter(transaction -> categoryId.equals(transaction.getCategoryId()))
                .findFirst().orElseThrow();

        interact(() -> categoryFilter.getSelectionModel().select(categoryName));
        assertEquals(List.of(matching.getId()), table.getItems().stream().map(Transaction::getId).toList());

        interact(() -> searchField.setText("does-not-match"));
        assertTrue(table.getItems().isEmpty());

        interact(() -> {
            searchField.setText("");
            minAmountField.setText("999");
            lookup("#maxAmountField").<TextField>query().setText("0");
            lookup("#datePresetFilter").<ComboBox<?>>query().getSelectionModel().select(6);
        });
        assertTrue(table.getItems().isEmpty());

        interact(lookup("#clearFiltersButton").<Button>query()::fire);

        assertTrue(table.getItems().stream().anyMatch(tx -> tx.getId().equals(matching.getId())));
        assertEquals(0, categoryFilter.getSelectionModel().getSelectedIndex());
        assertEquals("", searchField.getText());
        assertEquals("", minAmountField.getText());
        assertEquals("", lookup("#maxAmountField").<TextField>query().getText());
        assertEquals(0, lookup("#datePresetFilter").<ComboBox<?>>query().getSelectionModel().getSelectedIndex());
        assertNull(lookup("#fromDateFilter").<DatePicker>query().getValue());
        assertNull(lookup("#toDateFilter").<DatePicker>query().getValue());
        assertTrue(lookup("#applyFiltersButton").tryQuery().isEmpty());
        assertTrue(lookup("#clearFiltersButton").tryQuery().isPresent());
    }

    @Test
    void categoryActionOpensSearchablePicker() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        int rowIndex = indexOfTransactionWithCategory(table);
        Transaction selected = table.getItems().get(rowIndex);
        openPickerFromContextMenu(table, rowIndex, 0);

        assertNotNull(lookup("#pickerSearch").query());
        assertTrue(lookup("#pickerClearSelection").tryQuery().isEmpty());
        assertEquals(selected.getCategoryId(), lookup("#pickerList").<javafx.scene.control.ListView<Category>>query()
                .getSelectionModel().getSelectedItem().getId());
        fireEnterOnPicker();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#pickerSearch").tryQuery().isEmpty());
    }

    @Test
    void labelsActionOpensSearchablePickerWithCurrentLabels() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        int selectedIndex = indexOfTransactionWithLabels(table);
        Transaction selected = table.getItems().get(selectedIndex);
        org.fintrax.model.Label label = applicationContext.getBean(LabelService.class)
                .getLabel(selected.getLabelIds().iterator().next()).orElseThrow();
        openPickerFromContextMenu(table, selectedIndex, 1);

        assertNotNull(lookup("#pickerSearch").query());
        FlowPane selectedPane = lookup("#selectedLabelsPane").query();
        assertTrue(selectedPane.getChildren().size() > 0);
        assertEquals(2, selectedPane.getHgap());
        assertEquals(2, selectedPane.getVgap());
        HBox selectedChip = (HBox) selectedPane.getChildren().getFirst();
        assertEquals(1, selectedChip.getSpacing());
        javafx.scene.control.Label selectedLabelChip =
                (javafx.scene.control.Label) selectedChip.getChildren().getFirst();
        assertEquals(label.getName(), selectedLabelChip.getText());
        assertEquals(new Insets(2, 7, 2, 7), selectedLabelChip.getPadding());
        assertEquals(javafx.scene.paint.Color.web(label.getColor()),
                selectedLabelChip.getBackground().getFills().getFirst().getFill());

        interact(() -> lookup("#pickerSearch").<javafx.scene.control.TextField>query().setText(label.getName()));
        javafx.scene.control.ListView<org.fintrax.model.Label> options = lookup("#pickerList").query();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> options.getItems().size() == 1);
        javafx.scene.control.ListCell<org.fintrax.model.Label> optionCell = options.lookupAll(".list-cell").stream()
                .filter(javafx.scene.control.ListCell.class::isInstance)
                .map(node -> (javafx.scene.control.ListCell<org.fintrax.model.Label>) node)
                .filter(cell -> !cell.isEmpty() && label.getId().equals(cell.getItem().getId()))
                .findFirst()
                .orElseThrow();
        HBox optionContent = (HBox) optionCell.getGraphic();
        javafx.scene.control.Label optionLabelChip =
                (javafx.scene.control.Label) optionContent.getChildren().get(1);
        assertEquals(label.getName(), optionLabelChip.getText());
        assertEquals(javafx.scene.paint.Color.web(label.getColor()),
                optionLabelChip.getBackground().getFills().getFirst().getFill());
        interact(lookup("#pickerCancel").<Button>query()::fire);
    }

    @Test
    void transactionTableAllowsMultipleSelection() {
        TableView<Transaction> table = lookup("#transactionTable").query();

        assertEquals(SelectionMode.MULTIPLE, table.getSelectionModel().getSelectionMode());
    }

    @Test
    void columnMenuReflectsVisibilityAndPersistsChanges() throws Exception {
        MenuButton columnMenu = lookup("#columnMenuButton").query();
        TableView<Transaction> table = lookup("#transactionTable").query();

        assertSame(columnMenu, table.getColumns().getLast().getGraphic());
        List<CheckMenuItem> columnItems = columnMenu.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .toList();
        assertEquals(List.of("col.date", "col.payee", "col.purpose", "col.amount",
                        "col.balance", "col.category", "col.labels").stream().map(I18n::get).toList(),
                columnItems.stream().map(MenuItem::getText).toList());
        assertTrue(columnItems.stream().allMatch(CheckMenuItem::isSelected));
        assertEquals(I18n.get("button.columns.reset"), columnMenu.getItems().getLast().getText());

        var initialColumnOrder = List.copyOf(table.getColumns());
        Stage stage = (Stage) table.getScene().getWindow();
        Parent originalRoot = table.getScene().getRoot();
        CheckMenuItem purposeItem = (CheckMenuItem) columnMenu.getItems().get(2);
        try {
            interact(() -> purposeItem.setSelected(false));

            assertFalse(table.getColumns().get(2).isVisible());
            assertFalse(purposeItem.isSelected());
            assertTrue(applicationContext.getBean(SettingsService.class)
                    .getSettingValue("transaction.columns").contains("purpose=false"));

            interact(() -> {
                var dateColumn = table.getColumns().remove(0);
                table.getColumns().add(1, dateColumn);
            });
            assertEquals("payee,date,purpose,amount,balance,category,labels",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("transaction.columns.order"));

            Parent restoredView = applicationContext.getBean(ViewLoader.class).load("transactions");
            interact(() -> stage.getScene().setRoot(restoredView));
            TableView<Transaction> restoredTable = lookup("#transactionTable").query();
            assertEquals(List.of("col.payee", "col.date", "col.purpose", "col.amount",
                            "col.balance", "col.category", "col.labels").stream().map(I18n::get).toList(),
                    restoredTable.getColumns().stream().limit(7).map(TableColumn::getText).toList());
            assertFalse(restoredTable.getColumns().get(2).isVisible());

            MenuButton restoredMenu = lookup("#columnMenuButton").query();
            interact(() -> restoredTable.getSortOrder().add(restoredTable.getColumns().get(1)));
            assertFalse(restoredTable.getSortOrder().isEmpty());
            interact(restoredMenu.getItems().getLast()::fire);
            assertTrue(restoredTable.getColumns().stream().limit(7)
                    .allMatch(TableColumn::isVisible));
            assertTrue(restoredTable.getSortOrder().isEmpty());
            assertEquals("date=true,payee=true,purpose=true,amount=true,balance=true,category=true,labels=true",
                    applicationContext.getBean(SettingsService.class).getSettingValue("transaction.columns"));
            assertEquals("date,payee,purpose,amount,balance,category,labels",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("transaction.columns.order"));
        } finally {
            interact(() -> {
                purposeItem.setSelected(true);
                table.getColumns().setAll(initialColumnOrder);
                stage.getScene().setRoot(originalRoot);
            });
        }
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
                () -> lookup("#pickerSearch").tryQuery().isPresent());
        assertNotNull(lookup("#pickerSearch").query());
        javafx.scene.control.ListView<Category> categoryList = lookup("#pickerList").query();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !categoryList.getItems().isEmpty());
        interact(() -> categoryList.getSelectionModel().select(0));
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
                () -> lookup("#pickerSearch").tryQuery().isPresent());
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
                        I18n.get("contextMenu.setLabels")),
                contextMenu.getItems().stream().map(MenuItem::getText).toList());
        assertTrue(contextMenu.getItems().stream().allMatch(item -> item.getOnAction() != null));
    }

    @Test
    void creatingUnmatchedCategoryAssignsItToSelectedTransaction() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        Transaction selected = table.getItems().get(0);
        String name = "Picker category " + System.nanoTime();

        openPickerFromContextMenu(table, 0, 0);
        interact(() -> lookup("#pickerSearch").<javafx.scene.control.TextField>query().setText(name));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#pickerCreateButton").tryQuery().isPresent()
                        && lookup("#pickerCreateButton").<Button>query().isVisible());
        assertTrue(lookup("#pickerCreateButton").<Button>query().isVisible());
        interact(lookup("#pickerCreateButton").<Button>query()::fire);
        interact(lookup("#pickerSave").<Button>query()::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () ->
                applicationContext.getBean(CategoryService.class).getAllCategories().stream()
                        .anyMatch(category -> category.getName().equals(name)));

        Category created = applicationContext.getBean(CategoryService.class).getAllCategories().stream()
                .filter(category -> category.getName().equals(name)).findFirst().orElseThrow();
        assertEquals(created.getId(), applicationContext.getBean(TransactionService.class)
                .getTransaction(selected.getId()).orElseThrow().getCategoryId());
    }

    @Test
    void creatingUnmatchedLabelAddsItToSelectedTransaction() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        Transaction selected = table.getItems().get(0);
        String name = "Picker label " + System.nanoTime();

        openPickerFromContextMenu(table, 0, 1);
        interact(() -> lookup("#pickerSearch").<javafx.scene.control.TextField>query().setText(name));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#pickerCreateButton").tryQuery().isPresent()
                        && lookup("#pickerCreateButton").<Button>query().isVisible());
        interact(lookup("#pickerCreateButton").<Button>query()::fire);
        interact(lookup("#pickerSave").<Button>query()::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () ->
                applicationContext.getBean(LabelService.class).getAllLabels().stream()
                        .anyMatch(label -> label.getName().equals(name)));

        org.fintrax.model.Label created = applicationContext.getBean(LabelService.class).getAllLabels().stream()
                .filter(label -> label.getName().equals(name)).findFirst().orElseThrow();
        assertTrue(applicationContext.getBean(TransactionService.class)
                .getTransaction(selected.getId()).orElseThrow().getLabelIds().contains(created.getId()));
    }

    @Test
    void removingSelectedLabelChipRemovesLabelFromTransaction() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        Transaction selected = table.getItems().get(0);
        Set<Long> originalIds = Set.copyOf(selected.getLabelIds());
        assertFalse(originalIds.isEmpty());

        openPickerFromContextMenu(table, 0, 1);
        FlowPane selectedPane = lookup("#selectedLabelsPane").query();
        HBox firstChip = (HBox) selectedPane.getChildren().get(0);
        String removedName = ((javafx.scene.control.Label) firstChip.getChildren().get(0)).getText();
        org.fintrax.model.Label removedLabel = applicationContext.getBean(LabelService.class).getAllLabels().stream()
                .filter(label -> label.getName().equals(removedName)).findFirst().orElseThrow();
        Button removeButton = (Button) firstChip.getChildren().get(1);

        interact(removeButton::fire);
        acceptBulkEditDialog();

        Set<Long> updatedIds = applicationContext.getBean(TransactionService.class)
                .getTransaction(selected.getId()).orElseThrow().getLabelIds();
        assertFalse(updatedIds.contains(removedLabel.getId()));
        assertEquals(originalIds.size() - 1, updatedIds.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void categoryPickerCanClearAssignment() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        int selectedIndex = indexOfTransactionWithCategory(table);
        Transaction selected = table.getItems().get(selectedIndex);
        openPickerFromContextMenu(table, selectedIndex, 0);
        javafx.scene.control.ListView<Category> categoryList = lookup("#pickerList").query();
        Category currentCategory = applicationContext.getBean(CategoryService.class)
                .getCategory(selected.getCategoryId()).orElseThrow();
        interact(() -> lookup("#pickerSearch").<javafx.scene.control.TextField>query()
                .setText(currentCategory.getName()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> categoryList.getItems().size() == 1);
        javafx.scene.control.ListCell<Category> selectedCell = categoryList.lookupAll(".list-cell").stream()
                .filter(node -> node instanceof javafx.scene.control.ListCell<?> cell
                        && cell.getItem() != null
                        && currentCategory.getId().equals(((Category) cell.getItem()).getId()))
                .map(node -> (javafx.scene.control.ListCell<Category>) node)
                .findFirst().orElseThrow();
        clickOn(selectedCell);
        assertNull(categoryList.getSelectionModel().getSelectedItem());
        press(KeyCode.ENTER);

        assertNull(applicationContext.getBean(TransactionService.class)
                .getTransaction(selected.getId()).orElseThrow().getCategoryId());
    }

    @Test
    void typingExactCategoryAndPressingEnterAppliesIt() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        int rowIndex = indexOfTransactionWithCategory(table);
        Transaction selected = table.getItems().get(rowIndex);
        Category target = applicationContext.getBean(CategoryService.class)
                .createCategory("Fast edit " + System.nanoTime(), null, null);

        openPickerFromContextMenu(table, rowIndex, 0);
        interact(() -> lookup("#pickerSearch").<javafx.scene.control.TextField>query().setText(target.getName()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#pickerList").<javafx.scene.control.ListView<Category>>query()
                        .getSelectionModel().getSelectedItem() != null
                        && target.getId().equals(lookup("#pickerList")
                        .<javafx.scene.control.ListView<Category>>query()
                        .getSelectionModel().getSelectedItem().getId()));
        assertTrue(lookup("#pickerSave").<Button>query().isDefaultButton());
        fireEnterOnPicker();

        assertEquals(target.getId(), applicationContext.getBean(TransactionService.class)
                .getTransaction(selected.getId()).orElseThrow().getCategoryId());
    }

    @Test
    void detailPanelProvidesSearchableCategoryPickerAction() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        interact(() -> table.getSelectionModel().clearAndSelect(0));
        interact(lookup("#detailToggleButton").<Button>query()::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#detailCategoryButton").tryQuery().isPresent()
                        && lookup("#detailCategoryButton").<Button>query().isVisible());
        assertFalse(lookup("#detailCategoryButton").<Button>query().isDisabled());

        assertNotNull(lookup("#detailCategoryButton").<Button>query().getOnAction());
    }

    @Test
    void detailPanelProvidesSearchableLabelPickerAction() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        interact(() -> table.getSelectionModel().clearAndSelect(0));
        interact(lookup("#detailToggleButton").<Button>query()::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#detailLabelsButton").tryQuery().isPresent()
                        && lookup("#detailLabelsButton").<Button>query().isVisible());
        assertFalse(lookup("#detailLabelsButton").<Button>query().isDisabled());
        assertNotNull(lookup("#detailLabelsButton").<Button>query().getOnAction());
    }

    private void waitForPicker() throws TimeoutException {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#pickerSearch").tryQuery().isPresent());
    }

    private void openPickerFromContextMenu(TableView<Transaction> table, int rowIndex, int actionIndex)
            throws TimeoutException {
        interact(() -> table.getSelectionModel().clearAndSelect(rowIndex));
        Platform.runLater(() -> rowFor(table, rowIndex).getContextMenu().getItems().get(actionIndex).fire());
        waitForPicker();
    }

    private void fireEnterOnPicker() {
        interact(() -> {
            KeyEvent enter = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false);
            Event.fireEvent(lookup("#pickerSave").<Button>query().getScene().getRoot(), enter);
        });
    }

    private int indexOfTransactionWithCategory(TableView<Transaction> table) {
        for (int index = 0; index < table.getItems().size(); index++) {
            if (table.getItems().get(index).getCategoryId() != null) return index;
        }
        throw new AssertionError("Expected transaction with category");
    }

    private int indexOfTransactionWithLabels(TableView<Transaction> table) {
        for (int index = 0; index < table.getItems().size(); index++) {
            Set<Long> labelIds = table.getItems().get(index).getLabelIds();
            if (labelIds != null && !labelIds.isEmpty()) return index;
        }
        throw new AssertionError("Expected transaction with labels");
    }

    private void acceptBulkEditDialog() {
        interact(lookup("#pickerSave").<Button>query()::fire);
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
