package org.fintrax.ui;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.model.ActionType;
import org.fintrax.model.Category;
import org.fintrax.model.Label;
import org.fintrax.model.MatchField;
import org.fintrax.model.Operator;
import org.fintrax.model.Rule;
import org.fintrax.model.RuleAction;
import org.fintrax.model.RuleCondition;
import org.fintrax.model.Transaction;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.RuleService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.TransactionService;
import org.fintrax.ui.control.SearchableChoiceButton;
import org.kordamp.ikonli.javafx.FontIcon;
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
    private Category testCategory;
    private Transaction testCategoryTransaction;
    private Transaction detailSparseTransaction;

    @Override
    public void start(Stage stage) throws Exception {
        testCategory = applicationContext.getBean(CategoryService.class)
                .createCategory("Cell category " + System.nanoTime(), null, null);
        org.fintrax.model.Label label = applicationContext.getBean(LabelService.class)
                .createLabel("Cell label " + System.nanoTime(), "#3B82F6", null);
        org.fintrax.model.BankAccount detailAccount = applicationContext.getBean(AccountService.class)
                .createAccount("DE89370400440532013000", null, "Detail test bank", "Test holder", null, null);
        testCategoryTransaction = applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                .accountId(detailAccount.getId())
                .originalPayee("Cell popup test")
                .payeeDisplay("Display popup test")
                .purpose("Test transaction purpose")
                .amount(new BigDecimal("123.45"))
                .bookingDate(LocalDate.now())
                .valueDate(LocalDate.now().plusDays(1))
                .balanceAfter(new BigDecimal("678.90"))
                .transactionType("SEPA transfer")
                .categoryId(testCategory.getId())
                .labelIds(Set.of(label.getId()))
                .note("Test transaction note")
                .endToEndId("E2E-123")
                .mandateId("MANDATE-123")
                .creditorId("CREDITOR-123")
                .booked(false)
                .build());
        for (int i = 0; i < 2; i++) {
            Transaction transaction = applicationContext.getBean(TransactionService.class).createTransaction(Transaction.builder()
                    .originalPayee("Context menu test " + i + " " + System.nanoTime())
                    .bookingDate(LocalDate.now().minusDays(i + 1))
                    .amount(BigDecimal.valueOf(i + 2))
                    .build());
            if (i == 0) detailSparseTransaction = transaction;
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
    void transactionTableAlignsWithFilterInsideContentPanel() {
        VBox panel = lookup("#transactionsPanel").query();
        Node filter = lookup(".filter-panel").query();
        TableView<?> table = lookup("#transactionTable").query();

        Bounds filterBounds = filter.localToScene(filter.getBoundsInLocal());
        Bounds tableBounds = table.localToScene(table.getBoundsInLocal());

        assertTrue(panel.getStyleClass().contains("content-panel"));
        assertEquals(filterBounds.getMinX(), tableBounds.getMinX(), 1.0);
        assertEquals(filterBounds.getMaxX(), tableBounds.getMaxX(), 1.0);
    }

    @Test
    void transactionTableShowsHorizontalScrollbarWhenColumnsDoNotFit() throws TimeoutException {
        TableView<?> table = lookup("#transactionTable").query();
        double originalWindowWidth = table.getScene().getWindow().getWidth();

        try {
            interact(() -> table.getScene().getWindow().setWidth(640));

            ScrollBar horizontalScrollBar = table.lookupAll(".scroll-bar").stream()
                    .filter(ScrollBar.class::isInstance)
                    .map(ScrollBar.class::cast)
                    .filter(scrollBar -> scrollBar.getOrientation() == Orientation.HORIZONTAL)
                    .findFirst()
                    .orElseThrow();
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, horizontalScrollBar::isVisible);
            assertTrue(horizontalScrollBar.isVisible());
        } finally {
            interact(() -> table.getScene().getWindow().setWidth(originalWindowWidth));
        }
    }

    @Test
    void datePresetFillsCalendarPeriodBounds() {
        ComboBox<?> presets = lookup("#datePresetFilter").query();
        DatePicker fromDate = lookup("#fromDateFilter").query();
        DatePicker toDate = lookup("#toDateFilter").query();
        LocalDate today = LocalDate.now();

        interact(() -> {
            int previousYearIndex = presets.getItems().indexOf(presets.getItems().stream()
                    .filter(preset -> preset.toString().equals("PREVIOUS_YEAR"))
                    .findFirst().orElseThrow());
            presets.getSelectionModel().select(previousYearIndex);
        });

        assertEquals(LocalDate.of(today.getYear() - 1, 1, 1), fromDate.getValue());
        assertEquals(LocalDate.of(today.getYear() - 1, 12, 31), toDate.getValue());
    }

    @Test
    void additionalFiltersStartCollapsedWhileSearchAndDateFiltersRemainVisible() {
        assertFalse(lookup("#additionalFilters").query().isVisible());
        Button resetButton = lookup("#clearFiltersButton").query();
        Button toggleButton = lookup("#toggleAdditionalFiltersButton").query();
        assertTrue(toggleButton.isVisible());
        assertEquals("", resetButton.getText());
        assertEquals("", toggleButton.getText());
        assertEquals(I18n.get("button.clearFilters"), resetButton.getAccessibleText());
        assertEquals(I18n.get("button.showAdditionalFilters"), toggleButton.getAccessibleText());
        assertSame(resetButton.getParent(), toggleButton.getParent());
        assertEquals("fth-rotate-ccw", ((FontIcon) resetButton.getGraphic()).getIconLiteral());
        assertEquals("fth-chevron-down", ((FontIcon) toggleButton.getGraphic()).getIconLiteral());
        assertTrue(lookup("#searchField").query().isVisible());
        assertTrue(lookup("#datePresetFilter").query().isVisible());
        assertTrue(lookup("#fromDateFilter").query().isVisible());
        assertTrue(lookup("#toDateFilter").query().isVisible());
        assertTrue(lookup("#clearFiltersButton").query().isVisible());
        assertFalse(lookup("#collapsedFilterSummary").query().isVisible());
    }

    @Test
    void collapsedSummaryListsConfiguredAdditionalFiltersAndFiltersCanBeExpanded() {
        Button toggleFiltersButton = lookup("#toggleAdditionalFiltersButton").query();
        ComboBox<String> categoryFilter = lookup("#categoryFilter").query();
        String categoryName = categoryFilter.getItems().get(1);

        interact(toggleFiltersButton::fire);
        assertEquals("fth-chevron-up", ((FontIcon) toggleFiltersButton.getGraphic()).getIconLiteral());
        assertTrue(lookup("#additionalFilters").query().isVisible());
        interact(() -> {
            categoryFilter.getSelectionModel().select(categoryName);
            lookup("#minAmountField").<TextField>query().setText("25.00");
        });
        interact(toggleFiltersButton::fire);

        TextField summary = lookup("#collapsedFilterSummary").query();
        assertFalse(summary.isEditable());
        assertTrue(summary.isVisible());
        assertEquals(I18n.get("filter.summary.item", I18n.get("filter.category"), categoryName)
                        + " · "
                        + I18n.get("filter.summary.item", I18n.get("filter.minAmount"), "25.00"),
                summary.getText());

        interact(toggleFiltersButton::fire);
        assertTrue(lookup("#additionalFilters").query().isVisible());
        assertFalse(summary.isVisible());
    }

    @Test
    void transactionDateColumnsUseCurrentLocaleShortDateFormat() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        TableColumn<?, ?> bookingDateColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.bookingDate").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        TableColumn<?, ?> valueDateColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.valueDate").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        Transaction transaction = testCategoryTransaction;
        String expectedDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale())
                .format(transaction.getBookingDate());
        String expectedValueDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale())
                .format(transaction.getValueDate());
        interact(() -> table.scrollTo(table.getItems().indexOf(transaction)));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(expectedDate, transactionCell(table, bookingDateColumn, transaction).getText());
        assertEquals(expectedValueDate, transactionCell(table, valueDateColumn, transaction).getText());
    }

    @Test
    void doubleClickingTransactionDataCellOpensDetailsForThatTransaction() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        int rowIndex = table.getItems().indexOf(testCategoryTransaction);
        scrollToRow(table, rowIndex);
        TableColumn<?, ?> payeeColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.payee").equals(column.getText()))
                .findFirst()
                .orElseThrow();
        TableCell<?, ?> payeeCell = transactionCell(table, payeeColumn, testCategoryTransaction);

        assertFalse(lookup("#detailPanel").query().isVisible());
        doubleClickOn(payeeCell);

        assertTrue(lookup("#detailPanel").query().isVisible());
        assertEquals("Display popup test", detailText("#payeeValue"));
    }

    @Test
    void doubleClickingCategoryOrLabelCellsKeepsAssignmentDialogs() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        int rowIndex = table.getItems().indexOf(testCategoryTransaction);
        scrollToRow(table, rowIndex);

        for (String columnKey : List.of("col.category", "col.labels")) {
            TableColumn<?, ?> column = table.getColumns().stream()
                    .filter(candidate -> I18n.get(columnKey).equals(candidate.getText()))
                    .findFirst()
                    .orElseThrow();
            TableCell<?, ?> cell = transactionCell(table, column, testCategoryTransaction);

            doubleClickOn(cell);
            waitForPicker();
            assertFalse(lookup("#detailPanel").query().isVisible());
            interact(lookup("#pickerCancel").<Button>query()::fire);
        }
    }

    private TableCell<?, ?> transactionCell(TableView<Transaction> table, TableColumn<?, ?> column,
                                            Transaction transaction) {
        return table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == column
                        && cell.getTableRow() != null && cell.getTableRow().getItem() == transaction)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void transactionTableShowsAccountNameColumn() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        TableColumn<?, ?> accountColumn = table.getColumns().stream()
                .filter(column -> I18n.get("col.account").equals(column.getText()))
                .findFirst()
                .orElseThrow();

        interact(() -> table.scrollTo(table.getItems().indexOf(testCategoryTransaction)));
        WaitForAsyncUtils.waitForFxEvents();
        TableCell<?, ?> accountCell = accountCellFor(table, accountColumn, testCategoryTransaction.getId());
        assertEquals("Detail test bank (DE89****3000)", accountCell.getText());

        interact(() -> table.scrollTo(table.getItems().indexOf(detailSparseTransaction)));
        WaitForAsyncUtils.waitForFxEvents();
        TableCell<?, ?> sparseAccountCell = accountCellFor(table, accountColumn, detailSparseTransaction.getId());
        assertEquals("", sparseAccountCell.getText());
    }

    private TableCell<?, ?> accountCellFor(TableView<Transaction> table, TableColumn<?, ?> accountColumn,
                                           Long transactionId) {
        return table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty() && cell.getTableColumn() == accountColumn
                        && cell.getTableRow() != null
                        && cell.getTableRow().getItem() instanceof Transaction rowTransaction
                        && transactionId.equals(rowTransaction.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void transactionTableKeepsDateColumnFixedAndCellsSingleLineWithEllipsis() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        List<TableColumn<Transaction, ?>> dateColumns = table.getColumns().stream()
                .filter(column -> I18n.get("col.bookingDate").equals(column.getText())
                        || I18n.get("col.valueDate").equals(column.getText()))
                .toList();

        assertEquals(2, dateColumns.size());
        for (TableColumn<?, ?> dateColumn : dateColumns) {
            assertEquals(115, dateColumn.getMinWidth());
            assertEquals(115, dateColumn.getPrefWidth());
            assertEquals(115, dateColumn.getMaxWidth());
            assertFalse(dateColumn.isResizable());
        }

        var visibleCells = table.lookupAll(".table-cell").stream()
                .filter(TableCell.class::isInstance)
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> !cell.isEmpty())
                .toList();
        assertFalse(visibleCells.isEmpty());
        assertTrue(visibleCells.stream().allMatch(cell ->
                cell.getTextOverrun() == OverrunStyle.ELLIPSIS && !cell.isWrapText()));
    }

    @Test
    void applyingTypedDateRangeFiltersTransactions() {
        LocalDate matchingDate = LocalDate.of(2024, 2, 10);
        Transaction matching = applicationContext.getBean(TransactionService.class).createTransaction(
                Transaction.builder().originalPayee("date-filter-match-" + System.nanoTime())
                        .bookingDate(matchingDate).amount(BigDecimal.ONE).build());
        Transaction valueDateMatching = applicationContext.getBean(TransactionService.class).createTransaction(
                Transaction.builder().originalPayee("value-date-filter-match-" + System.nanoTime())
                        .bookingDate(matchingDate.minusDays(1)).valueDate(matchingDate).amount(BigDecimal.ONE).build());
        Transaction outsideRange = applicationContext.getBean(TransactionService.class).createTransaction(
                Transaction.builder().originalPayee("date-filter-outside-" + System.nanoTime())
                        .bookingDate(matchingDate.plusDays(1)).valueDate(matchingDate.plusDays(2))
                        .amount(BigDecimal.ONE).build());
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
        assertFalse(table.getItems().stream().anyMatch(tx -> tx.getId().equals(valueDateMatching.getId())));
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
        interact(lookup("#toggleAdditionalFiltersButton").<Button>query()::fire);
        String categoryName = testCategory.getName();
        Transaction matching = table.getItems().stream()
                .filter(transaction -> testCategoryTransaction.getId().equals(transaction.getId()))
                .findFirst().orElseThrow();

        interact(() -> categoryFilter.getSelectionModel().select(categoryName));
        assertEquals(List.of(matching.getId()), table.getItems().stream().map(Transaction::getId).toList());

        interact(() -> searchField.setText("does-not-match"));
        assertTrue(table.getItems().isEmpty());

        interact(() -> {
            searchField.setText("");
            minAmountField.setText("999");
            lookup("#maxAmountField").<TextField>query().setText("0");
            ComboBox<?> presets = lookup("#datePresetFilter").query();
            int last7DaysIndex = presets.getItems().indexOf(presets.getItems().stream()
                    .filter(preset -> preset.toString().equals("LAST_7_DAYS"))
                    .findFirst().orElseThrow());
            presets.getSelectionModel().select(last7DaysIndex);
        });
        assertTrue(table.getItems().isEmpty());

        interact(lookup("#toggleAdditionalFiltersButton").<Button>query()::fire);
        assertTrue(lookup("#collapsedFilterSummary").query().isVisible());
        interact(lookup("#clearFiltersButton").<Button>query()::fire);

        assertTrue(table.getItems().stream().anyMatch(tx -> tx.getId().equals(matching.getId())));
        assertEquals(0, categoryFilter.getSelectionModel().getSelectedIndex());
        assertEquals("", searchField.getText());
        assertEquals("", minAmountField.getText());
        assertEquals("", lookup("#maxAmountField").<TextField>query().getText());
        assertEquals(0, lookup("#datePresetFilter").<ComboBox<?>>query().getSelectionModel().getSelectedIndex());
        assertNull(lookup("#fromDateFilter").<DatePicker>query().getValue());
        assertNull(lookup("#toDateFilter").<DatePicker>query().getValue());
        assertEquals("", lookup("#collapsedFilterSummary").<TextField>query().getText());
        assertFalse(lookup("#additionalFilters").query().isVisible());
        assertFalse(lookup("#collapsedFilterSummary").query().isVisible());
        assertTrue(lookup("#applyFiltersButton").tryQuery().isEmpty());
        assertTrue(lookup("#clearFiltersButton").tryQuery().isPresent());
    }

    @Test
    void searchFieldMatchesCategoryLabelAmountAndDisplayedDate() {
        TableView<Transaction> table = lookup("#transactionTable").query();
        TextField searchField = lookup("#searchField").query();
        Label label = applicationContext.getBean(LabelService.class)
                .getLabel(testCategoryTransaction.getLabelIds().iterator().next()).orElseThrow();
        String displayedDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale())
                .format(testCategoryTransaction.getBookingDate());
        String displayedAmount = String.format("%,.2f EUR", testCategoryTransaction.getAmount().doubleValue());

        for (String searchText : List.of(testCategory.getName(), label.getName(), displayedAmount, displayedDate)) {
            interact(() -> searchField.setText(searchText));
            assertTrue(table.getItems().stream()
                    .anyMatch(transaction -> transaction.getId().equals(testCategoryTransaction.getId())),
                    "search should match: " + searchText);
        }
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
        assertEquals(List.of("col.bookingDate", "col.valueDate", "col.payee", "col.account", "col.purpose",
                        "col.amount", "col.balance", "col.category", "col.labels").stream()
                        .map(I18n::get).toList(),
                columnItems.stream().map(MenuItem::getText).toList());
        assertTrue(columnItems.stream().allMatch(CheckMenuItem::isSelected));
        assertEquals(I18n.get("button.columns.reset"), columnMenu.getItems().getLast().getText());

        var initialColumnOrder = List.copyOf(table.getColumns());
        Stage stage = (Stage) table.getScene().getWindow();
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
                    .getSettingValue("transaction.columns").contains("purpose=false"));

            interact(() -> {
                var bookingDateColumn = table.getColumns().remove(0);
                table.getColumns().add(1, bookingDateColumn);
            });
            assertEquals("valueDate,date,payee,account,purpose,amount,balance,category,labels",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("transaction.columns.order"));

            TableColumn<Transaction, ?> dateColumn = table.getColumns().stream()
                    .filter(column -> I18n.get("col.bookingDate").equals(column.getText()))
                    .findFirst().orElseThrow();
            TableColumn<Transaction, ?> amountColumn = table.getColumns().stream()
                    .filter(column -> I18n.get("col.amount").equals(column.getText()))
                    .findFirst().orElseThrow();
            interact(() -> {
                dateColumn.setSortType(TableColumn.SortType.DESCENDING);
                amountColumn.setSortType(TableColumn.SortType.ASCENDING);
                table.getSortOrder().setAll(dateColumn, amountColumn);
            });
            assertEquals("date=DESCENDING,amount=ASCENDING",
                    applicationContext.getBean(SettingsService.class)
                            .getSettingValue("transaction.columns.sort"));

            Parent restoredView = applicationContext.getBean(ViewLoader.class).load("transactions");
            interact(() -> stage.getScene().setRoot(restoredView));
            TableView<Transaction> restoredTable = lookup("#transactionTable").query();
            assertEquals(List.of("col.valueDate", "col.bookingDate", "col.payee", "col.account", "col.purpose",
                            "col.amount", "col.balance", "col.category", "col.labels").stream()
                            .map(I18n::get).toList(),
                    restoredTable.getColumns().stream().limit(9).map(TableColumn::getText).toList());
            assertFalse(restoredTable.getColumns().stream()
                    .filter(column -> I18n.get("col.purpose").equals(column.getText()))
                    .findFirst().orElseThrow().isVisible());
            assertEquals(List.of("col.bookingDate", "col.amount").stream().map(I18n::get).toList(),
                    restoredTable.getSortOrder().stream().map(TableColumn::getText).toList());
            assertEquals(List.of(TableColumn.SortType.DESCENDING, TableColumn.SortType.ASCENDING),
                    restoredTable.getSortOrder().stream().map(TableColumn::getSortType).toList());
            interact(() -> {
                FintraxUI.configureScene(restoredTable.getScene());
                restoredTable.applyCss();
            });
            for (TableColumn<Transaction, ?> sortedColumn : restoredTable.getSortOrder()) {
                Node header = tableColumnHeader(restoredTable, sortedColumn.getText());
                Node sortArrow = header.lookup(".arrow");
                assertNotNull(sortArrow);
                assertTrue(sortArrow.isVisible());
                assertSortDotsCentered(header, sortArrow);
            }

            MenuButton restoredMenu = lookup("#columnMenuButton").query();
            assertFalse(restoredTable.getSortOrder().isEmpty());
            interact(restoredMenu.getItems().getLast()::fire);
            assertTrue(restoredTable.getColumns().stream().limit(9)
                    .allMatch(TableColumn::isVisible));
            assertTrue(restoredTable.getSortOrder().isEmpty());
            assertEquals("", applicationContext.getBean(SettingsService.class)
                    .getSettingValue("transaction.columns.sort"));
            assertEquals("date=true,valueDate=true,payee=true,account=true,purpose=true,amount=true,balance=true,category=true,labels=true",
                    applicationContext.getBean(SettingsService.class).getSettingValue("transaction.columns"));
            assertEquals("date,valueDate,payee,account,purpose,amount,balance,category,labels",
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
        assertEquals(List.of(0, 1), table.getSelectionModel().getSelectedIndices().stream().sorted().toList());
        MenuItem setLabelsItem = rowFor(table, 0).getContextMenu().getItems().stream()
                .filter(item -> I18n.get("contextMenu.setLabels").equals(item.getText()))
                .findFirst()
                .orElseThrow();
        Platform.runLater(setLabelsItem::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#selectedLabelsPane").tryQuery().isPresent());
        assertNotNull(lookup("#pickerSearch").query());
        acceptBulkEditDialog();

        assertEquals(selectedIds, table.getSelectionModel().getSelectedItems().stream()
                .map(Transaction::getId)
                .toList());
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
                        I18n.get("contextMenu.setLabels"),
                        I18n.get("contextMenu.createRule")),
                contextMenu.getItems().stream().map(MenuItem::getText).toList());
        assertTrue(contextMenu.getItems().stream().allMatch(item -> item.getOnAction() != null));
    }

    @Test
    void creatingRuleFromTransactionPrefillsEditableConditionsAndActions() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        int rowIndex = table.getItems().indexOf(testCategoryTransaction);
        assertTrue(rowIndex >= 0);
        Category replacementCategory = applicationContext.getBean(CategoryService.class)
                .createCategory("Rule dialog category " + System.nanoTime(), null, null);

        scrollToRow(table, rowIndex);
        Platform.runLater(() -> rowFor(table, rowIndex).getContextMenu().getItems().get(2).fire());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#createRuleSave").tryQuery().isPresent());

        List<HBox> conditions = rows(".rule-condition-row");
        assertEquals(2, conditions.size());
        HBox purposeCondition = conditions.stream()
                .filter(row -> comboBoxAt(row, 0).getValue().equals(MatchField.PURPOSE.name()))
                .findFirst().orElseThrow();
        assertEquals("Test transaction purpose", ((TextField) purposeCondition.getChildren().get(2)).getText());

        interact(() -> {
            comboBoxAt(purposeCondition, 1).setValue(Operator.EQUALS.name());
            ((TextField) purposeCondition.getChildren().get(2)).setText("Edited transaction purpose");
        });

        HBox categoryAction = rows(".rule-action-row").stream()
                .filter(row -> comboBoxAt(row, 0).getValue().equals(ActionType.ASSIGN_CATEGORY.name()))
                .findFirst().orElseThrow();
        SearchableChoiceButton categoryChoice = (SearchableChoiceButton) categoryAction.getChildren().get(1);
        assertEquals(testCategory.getName(), categoryChoice.getValue());
        chooseSearchableChoice(categoryChoice, replacementCategory.getName());
        assertEquals(replacementCategory.getName(), categoryChoice.getValue());

        interact(lookup("#createRuleSave").<Button>query()::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> applicationContext.getBean(RuleService.class)
                .getAllRules().stream().anyMatch(rule -> rule.getName().equals("Rule for Display popup test")));

        Rule created = applicationContext.getBean(RuleService.class).getAllRules().stream()
                .filter(rule -> rule.getName().equals("Rule for Display popup test"))
                .findFirst().orElseThrow();
        assertEquals(List.of(MatchField.PAYEE_NAME, MatchField.PURPOSE),
                created.getConditions().stream().map(RuleCondition::getMatchField).toList());
        assertEquals(List.of("Display popup test", "Edited transaction purpose"),
                created.getConditions().stream().map(RuleCondition::getValue).toList());
        assertEquals(List.of(Operator.CONTAINS, Operator.EQUALS),
                created.getConditions().stream().map(RuleCondition::getOperator).toList());
        assertEquals(1, created.getActions().stream()
                .filter(action -> action.getActionType() == ActionType.ASSIGN_CATEGORY
                        && replacementCategory.getId().equals(action.getCategoryId())).count());
        assertTrue(created.getActions().stream().anyMatch(action -> action.getActionType() == ActionType.ADD_LABEL
                && testCategoryTransaction.getLabelIds().contains(action.getLabelId())));
    }

    @Test
    void creatingRuleWithoutTransactionActionsRequiresChoosingAnAction() throws TimeoutException {
        TableView<Transaction> table = lookup("#transactionTable").query();
        int rowIndex = table.getItems().indexOf(detailSparseTransaction);
        assertTrue(rowIndex >= 0);

        scrollToRow(table, rowIndex);
        Platform.runLater(() -> rowFor(table, rowIndex).getContextMenu().getItems().get(2).fire());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#createRuleSave").tryQuery().isPresent());
        assertTrue(lookup("#createRuleSave").<Button>query().isDisabled());
        assertTrue(rows(".rule-action-row").isEmpty());

        String categoryName = "Created category action " + System.nanoTime();
        String labelName = "Created label action " + System.nanoTime();
        interact(lookup("#addRuleActionButton").<Button>query()::fire);
        HBox categoryAction = rows(".rule-action-row").get(0);
        interact(() -> comboBoxAt(categoryAction, 0).setValue(ActionType.ASSIGN_CATEGORY.name()));
        createSearchableChoice((SearchableChoiceButton) categoryAction.getChildren().get(1), categoryName);

        interact(lookup("#addRuleActionButton").<Button>query()::fire);
        HBox labelAction = rows(".rule-action-row").get(1);
        interact(() -> comboBoxAt(labelAction, 0).setValue(ActionType.ADD_LABEL.name()));
        createSearchableChoice((SearchableChoiceButton) labelAction.getChildren().get(1), labelName);
        assertFalse(lookup("#createRuleSave").<Button>query().isDisabled());

        String ruleName = lookup("#ruleNameField").<TextField>query().getText();
        interact(lookup("#createRuleSave").<Button>query()::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> applicationContext.getBean(RuleService.class)
                .getAllRules().stream().anyMatch(rule -> rule.getName().equals(ruleName)));

        Rule created = applicationContext.getBean(RuleService.class).getAllRules().stream()
                .filter(rule -> rule.getName().equals(ruleName)).findFirst().orElseThrow();
        Category createdCategory = applicationContext.getBean(CategoryService.class).getAllCategories().stream()
                .filter(category -> category.getName().equals(categoryName)).findFirst().orElseThrow();
        org.fintrax.model.Label createdLabel = applicationContext.getBean(LabelService.class).getAllLabels().stream()
                .filter(label -> label.getName().equals(labelName)).findFirst().orElseThrow();
        assertEquals(createdCategory.getId(), created.getActions().stream()
                .filter(action -> action.getActionType() == ActionType.ASSIGN_CATEGORY)
                .map(RuleAction::getCategoryId).findFirst().orElseThrow());
        assertEquals(createdLabel.getId(), created.getActions().stream()
                .filter(action -> action.getActionType() == ActionType.ADD_LABEL)
                .map(RuleAction::getLabelId).findFirst().orElseThrow());
        assertEquals("#1976d2", createdLabel.getColor());
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
    void detailPanelShowsTransactionProperties() {
        showTransactionDetail(testCategoryTransaction);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        assertEquals("Detail test bank (DE89****3000)", detailText("#accountValue"));
        assertEquals(LocalDate.now().format(dateFormatter), detailText("#bookingDateValue"));
        assertEquals(LocalDate.now().plusDays(1).format(dateFormatter), detailText("#valueDateValue"));
        assertEquals("Cell popup test", detailText("#originalPayeeValue"));
        assertEquals("Test transaction purpose", detailText("#purposeValue"));
        assertEquals(String.format(I18n.getCurrentLocale(), "%,.2f EUR", new BigDecimal("123.45")),
                detailText("#amountValue"));
        assertEquals(String.format(I18n.getCurrentLocale(), "%,.2f EUR", new BigDecimal("678.90")),
                detailText("#balanceValue"));
        assertEquals(testCategory.getName(), detailText("#categoryValue"));
        org.fintrax.model.Label testLabel = applicationContext.getBean(LabelService.class)
                .getLabel(testCategoryTransaction.getLabelIds().iterator().next()).orElseThrow();
        FlowPane labelsValue = lookup("#labelsValue").query();
        assertEquals(testLabel.getName(), ((javafx.scene.control.Label)
                labelsValue.getChildren().getFirst()).getText());
        assertEquals("Test transaction note", detailText("#noteValue"));
        assertEquals("SEPA transfer", detailText("#transactionTypeValue"));
        assertEquals(I18n.get("detail.status.pending"), detailText("#bookingStatusValue"));
        assertEquals(I18n.get("detail.bookingStatus"), detailText("#bookingStatusKey"));
        assertTrue(lookup("#bookingStatusValue").<javafx.scene.control.Label>query()
                .getStyleClass().contains("pending"));
        assertTrue(lookup("#originalPayeeKey").<javafx.scene.control.Label>query().isWrapText());
        assertEquals("E2E-123", detailText("#endToEndIdValue"));
        assertEquals("MANDATE-123", detailText("#mandateIdValue"));
        assertEquals("CREDITOR-123", detailText("#creditorIdValue"));
        assertEquals("Display popup test", detailText("#payeeValue"));
    }

    @Test
    void detailPropertiesUseRequestedCompactPairingsAndFullWidthText() {
        showTransactionDetail(testCategoryTransaction);

        assertSideBySide("#amountValue", "#balanceValue");
        assertSideBySide("#categoryValue", "#labelsValue");
        assertSideBySide("#bookingDateValue", "#valueDateValue");
        assertSideBySide("#payeeValue", "#originalPayeeValue");
        assertEquals(3, GridPane.getColumnSpan(lookup("#noteValue").query()));
        assertEquals(3, GridPane.getColumnSpan(lookup("#purposeValue").query()));
        Set<Node> rowDividers = lookup("#detailForm").<GridPane>query()
                .lookupAll(".detail-row-separator");
        assertEquals(8, rowDividers.size());
        assertTrue(rowDividers.stream().allMatch(divider -> GridPane.getColumnIndex(divider) == 0
                && GridPane.getColumnSpan(divider) == 4));
        assertTrue(GridPane.getRowIndex(lookup("#amountValue").query())
                < GridPane.getRowIndex(lookup("#categoryValue").query()));
        assertTrue(GridPane.getRowIndex(lookup("#categoryValue").query())
                < GridPane.getRowIndex(lookup("#noteValue").query()));
        assertTrue(GridPane.getRowIndex(lookup("#noteValue").query())
                < GridPane.getRowIndex(lookup("#bookingDateValue").query()));
    }

    @Test
    void detailPanelDoesNotExposeEditablePropertiesOrSaveAction() {
        showTransactionDetail(testCategoryTransaction);

        assertFalse(lookup("#payeeField").tryQuery().isPresent());
        assertFalse(lookup("#noteArea").tryQuery().isPresent());
        assertFalse(lookup("#detailCategoryButton").tryQuery().isPresent());
        assertFalse(lookup("#detailLabelsButton").tryQuery().isPresent());
        assertFalse(lookup("#detailSaveButton").tryQuery().isPresent());
    }

    @Test
    void openingDetailsResizesPanelToShowAllProperties() throws TimeoutException {
        showTransactionDetail(testCategoryTransaction);
        javafx.scene.control.ScrollPane detailScrollPane = lookup("#detailScrollPane").query();
        GridPane detailForm = lookup("#detailForm").query();

        try {
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
                double width = detailScrollPane.getViewportBounds().getWidth();
                return width > 0 && detailScrollPane.getViewportBounds().getHeight() + 1
                        >= detailForm.prefHeight(width);
            });
        } catch (TimeoutException timeout) {
            double width = detailScrollPane.getViewportBounds().getWidth();
            throw new AssertionError("Details viewport=" + detailScrollPane.getViewportBounds().getHeight()
                    + ", content=" + detailForm.prefHeight(width) + ", panel=" + detailPanelHeight(), timeout);
        }
        double width = detailScrollPane.getViewportBounds().getWidth();
        assertTrue(detailScrollPane.getViewportBounds().getHeight() + 1 >= detailForm.prefHeight(width));
    }

    private double detailPanelHeight() {
        return lookup("#detailPanel").query().getBoundsInParent().getHeight();
    }

    @Test
    void detailPanelKeepsUnavailablePropertiesWithEmptyValues() {
        showTransactionDetail(detailSparseTransaction);

        assertPropertyEmpty("#accountKey", "#accountValue");
        assertPropertyEmpty("#valueDateKey", "#valueDateValue");
        assertPropertyEmpty("#purposeKey", "#purposeValue");
        assertPropertyEmpty("#balanceKey", "#balanceValue");
        assertPropertyEmpty("#transactionTypeKey", "#transactionTypeValue");
        assertPropertyEmpty("#categoryKey", "#categoryValue");
        assertPropertyEmpty("#labelsKey", "#labelsValue");
        assertPropertyEmpty("#noteKey", "#noteValue");
        assertPropertyEmpty("#payeeKey", "#payeeValue");
        assertPropertyEmpty("#endToEndIdKey", "#endToEndIdValue");
        assertPropertyEmpty("#mandateIdKey", "#mandateIdValue");
        assertPropertyEmpty("#creditorIdKey", "#creditorIdValue");
        assertTrue(detailPropertyIsVisible("#bookingDateKey", "#bookingDateValue"));
        assertTrue(detailPropertyIsVisible("#originalPayeeKey", "#originalPayeeValue"));
        assertEquals(2, GridPane.getColumnIndex(lookup("#originalPayeeKey").query()));
        assertEquals(3, GridPane.getColumnIndex(lookup("#originalPayeeValue").query()));
        assertTrue(lookup("#labelsValue").<FlowPane>query().getChildren().isEmpty());
    }

    private String detailText(String selector) {
        return lookup(selector).<Labeled>query().getText();
    }

    private void assertPropertyEmpty(String keySelector, String valueSelector) {
        assertTrue(detailPropertyIsVisible(keySelector, valueSelector));
        Node value = lookup(valueSelector).query();
        if (value instanceof Labeled labeled) assertEquals("", labeled.getText());
        if (value instanceof FlowPane labels) assertTrue(labels.getChildren().isEmpty());
    }

    private boolean detailPropertyIsVisible(String keySelector, String valueSelector) {
        Node key = lookup(keySelector).query();
        Node value = lookup(valueSelector).query();
        return key.isVisible() && key.isManaged() && value.isVisible() && value.isManaged();
    }

    private void showTransactionDetail(Transaction transaction) {
        TableView<Transaction> table = lookup("#transactionTable").query();
        int row = table.getItems().stream().map(Transaction::getId).toList().indexOf(transaction.getId());
        if (row < 0) throw new AssertionError("Expected transaction to be visible in the table");
        interact(() -> table.getSelectionModel().clearAndSelect(row));
        interact(lookup("#detailToggleButton").<Button>query()::fire);
    }

    private void assertSideBySide(String leftSelector, String rightSelector) {
        Node left = lookup(leftSelector).query();
        Node right = lookup(rightSelector).query();
        assertEquals(GridPane.getRowIndex(left), GridPane.getRowIndex(right));
        assertEquals(1, GridPane.getColumnIndex(left));
        assertEquals(3, GridPane.getColumnIndex(right));
    }

    private void waitForPicker() throws TimeoutException {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#pickerSearch").tryQuery().isPresent());
    }

    private void scrollToRow(TableView<Transaction> table, int rowIndex) throws TimeoutException {
        interact(() -> table.scrollTo(rowIndex));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
            try {
                return rowFor(table, rowIndex).getItem() != null;
            } catch (java.util.NoSuchElementException ignored) {
                return false;
            }
        });
    }

    private List<HBox> rows(String selector) {
        return lookup(selector).queryAll().stream()
                .filter(HBox.class::isInstance)
                .map(HBox.class::cast)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> comboBoxAt(HBox row, int index) {
        return (ComboBox<String>) row.getChildren().get(index);
    }

    private void chooseSearchableChoice(SearchableChoiceButton control, String value) throws TimeoutException {
        interact(control::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#searchableChoiceSearch").tryQuery().isPresent());
        TextField searchField = lookup("#searchableChoiceSearch").query();
        assertEquals("", searchField.getText());
        interact(() -> searchField.setText(value));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () ->
                lookup("#searchableChoiceList").<ListView<?>>query().getItems().contains(value));
        interact(() -> {
            ListView<?> choices = lookup("#searchableChoiceList").query();
            choices.getSelectionModel().select(choices.getItems().indexOf(value));
            Event.fireEvent(searchField, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> value.equals(control.getValue()));
    }

    private void createSearchableChoice(SearchableChoiceButton control, String value) throws TimeoutException {
        interact(control::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#searchableChoiceSearch").tryQuery().isPresent());
        TextField searchField = lookup("#searchableChoiceSearch").query();
        assertEquals("", searchField.getText());
        interact(() -> searchField.setText(value));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> lookup("#searchableChoiceCreate").tryQuery()
                .map(Button.class::cast).filter(Button::isVisible).isPresent());
        interact(lookup("#searchableChoiceCreate").<Button>query()::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> value.equals(control.getValue()));
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

    private Node tableColumnHeader(TableView<?> table, String title) {
        return table.lookupAll(".column-header").stream()
                .filter(header -> header.lookup(".label") instanceof Labeled label
                        && title.equals(label.getText()))
                .findFirst()
                .orElseThrow();
    }

    private void assertSortDotsCentered(Node header, Node sortArrow) {
        Node sortDots = header.lookup(".sort-order-dots-container");
        assertNotNull(sortDots);
        Bounds arrowBounds = sortArrow.localToScene(sortArrow.getBoundsInLocal());
        Bounds dotsBounds = sortDots.localToScene(sortDots.getBoundsInLocal());
        double arrowCenterX = arrowBounds.getMinX() + arrowBounds.getWidth() / 2;
        double dotsCenterX = dotsBounds.getMinX() + dotsBounds.getWidth() / 2;
        assertEquals(arrowCenterX, dotsCenterX, 0.5,
                "sort-order dots should be centered over the sort arrow");
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
