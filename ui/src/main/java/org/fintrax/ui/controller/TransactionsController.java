package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.*;
import org.fintrax.ui.ViewLoader;
import org.fintrax.ui.control.AccountDisplay;
import org.fintrax.ui.control.LabelChipFactory;
import org.fintrax.ui.control.SearchableDropdownPicker;
import org.fintrax.ui.control.SingleLineText;
import org.fintrax.ui.control.TableCellTooltips;
import org.fintrax.ui.control.TableColumnConfig;
import org.fintrax.ui.control.TableColumnVisibilityMenu;
import org.fintrax.ui.control.TableViewResizePolicy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
public class TransactionsController {
    private static final String COLUMN_CONFIG_KEY = "transaction.columns";
    private static final String AMOUNT_POSITIVE_STYLE_CLASS = "amount-positive";
    private static final String AMOUNT_NEGATIVE_STYLE_CLASS = "amount-negative";
    @FXML
    private BorderPane rootPane;
    @FXML
    private TransactionWorkspaceController transactionWorkspaceController;

    private TransactionFilterPanelController transactionFilterPanelController;
    private TableView<Transaction> transactionTable;

    @FXML
    private MenuButton columnMenuButton;
    @FXML
    private TableColumn<Transaction, LocalDate> bookingDateColumn;
    @FXML
    private TableColumn<Transaction, LocalDate> valueDateColumn;
    @FXML
    private TableColumn<Transaction, String> payeeColumn;
    @FXML
    private TableColumn<Transaction, String> accountColumn;
    @FXML
    private TableColumn<Transaction, String> purposeColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> amountColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> balanceColumn;
    @FXML
    private TableColumn<Transaction, String> categoryColumn;
    @FXML
    private TableColumn<Transaction, String> labelsColumn;
    @FXML
    private TableColumn<Transaction, ?> columnMenuColumn;


    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final SettingsService settingsService;
    private final RuleService ruleService;
    private final ViewLoader viewLoader;
    private final TransactionTableActions transactionTableActions;

    private final ObservableList<Transaction> tableData = FXCollections.observableArrayList();
    private final SettledTransactionIds settledTransactionIds;

    public TransactionsController(TransactionService transactionService, AccountService accountService,
                                  CategoryService categoryService, LabelService labelService,
                                  SettingsService settingsService, RuleService ruleService,
                                  ViewLoader viewLoader, TransactionTableActions transactionTableActions,
                                  SettledTransactionIds settledTransactionIds) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.settingsService = settingsService;
        this.ruleService = ruleService;
        this.viewLoader = viewLoader;
        this.transactionTableActions = transactionTableActions;
        this.settledTransactionIds = settledTransactionIds;
    }

    @FXML
    public void initialize() {
        log.info("TransactionsController initialized");
        transactionTable = transactionWorkspaceController.table();
        transactionFilterPanelController = transactionWorkspaceController.filterPanel();
        transactionWorkspaceController.setHeadlineAsPageTitle();
        transactionWorkspaceController.setDetailToggleVisible(true);
        transactionWorkspaceController.setTableId("transactionTable");
        transactionWorkspaceController.setTableColumns(List.of(bookingDateColumn, valueDateColumn, payeeColumn,
                accountColumn, purposeColumn, amountColumn, balanceColumn, categoryColumn, labelsColumn,
                columnMenuColumn));
        transactionWorkspaceController.attachDetails(Set.of(categoryColumn, labelsColumn));
        transactionTableActions.installContextMenu(
                transactionTable, this::reloadTransactionsKeepingSelection, this::createRuleFromTransaction);
        setupTableColumns();
        setupFutureTransactionRows();
        setupUnviewedTransactionRows();
        installSelectionListener();
        TableCellTooltips.install(transactionTable);
        transactionFilterPanelController.setCollapsible(true);
        transactionFilterPanelController.restoreForView(SessionViewState.TRANSACTIONS_VIEW);
        transactionFilterPanelController.setOnFiltersChanged(this::loadTransactions);
        loadTransactions();
        setupColumnMenu();
        Platform.runLater(rootPane::applyCss);
    }

    private void setupTableColumns() {
        TableViewResizePolicy.fitColumnsAndAllowHorizontalScrolling(transactionTable);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        setupDateColumn(bookingDateColumn, Transaction::getBookingDate, dateFormatter);
        setupDateColumn(valueDateColumn, Transaction::getValueDate, dateFormatter);

        payeeColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getPayeeDisplay() != null ? cd.getValue().getPayeeDisplay() : cd.getValue().getOriginalPayee()));

        accountColumn.setCellValueFactory(cd -> {
            Long accountId = cd.getValue().getAccountId();
            if (accountId == null) return new SimpleStringProperty("");
            return new SimpleStringProperty(AccountDisplay.name(
                    accountService.getAccount(accountId).orElse(null)));
        });

        purposeColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                SingleLineText.normalize(cd.getValue().getPurpose())));

        amountColumn.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getAmount()));
        amountColumn.setCellFactory(col -> new TableCell<>() {
            {
                setAlignment(Pos.CENTER_RIGHT);
            }

            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%,.2f", item.doubleValue()));
                    getStyleClass().removeAll(AMOUNT_POSITIVE_STYLE_CLASS, AMOUNT_NEGATIVE_STYLE_CLASS);
                    if (item.signum() < 0) {
                        getStyleClass().add(AMOUNT_NEGATIVE_STYLE_CLASS);
                    } else {
                        getStyleClass().add(AMOUNT_POSITIVE_STYLE_CLASS);
                    }
                }
            }
        });

        balanceColumn.setCellValueFactory(cd -> new SimpleObjectProperty<>(isFutureTransaction(cd.getValue())
                ? null : cd.getValue().getBalanceAfter()));
        balanceColumn.setCellFactory(col -> new TableCell<>() {
            {
                setAlignment(Pos.CENTER_RIGHT);
            }

            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.2f", item.doubleValue()));
            }
        });

        categoryColumn.setCellValueFactory(cd -> {
            Long catId = cd.getValue().getCategoryId();
            if (catId == null) return new SimpleStringProperty("");
            return new SimpleStringProperty(categoryService.getCategory(catId)
                    .map(Category::getName).orElse(""));
        });
        categoryColumn.setCellFactory(column -> createAssignmentCell(this::onBulkCategory));

        labelsColumn.setCellValueFactory(cd -> {
            Set<Long> labelIds = cd.getValue().getLabelIds();
            if (labelIds == null || labelIds.isEmpty()) return new SimpleStringProperty("");
            String text = labelIds.stream()
                    .map(id -> labelService.getLabel(id).map(org.fintrax.model.Label::getName).orElse(""))
                    .filter(s -> !((String) s).isEmpty())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(text);
        });
        labelsColumn.setCellFactory(column -> createLabelChipCell(this::onSetLabels));
    }

    private void setupFutureTransactionRows() {
        var existingRowFactory = transactionTable.getRowFactory();
        transactionTable.setRowFactory(table -> {
            TableRow<Transaction> row = existingRowFactory == null
                    ? new TableRow<>() : existingRowFactory.call(table);
            row.itemProperty().addListener((observable, oldTransaction, newTransaction) ->
                    updateFutureTransactionStyle(row, newTransaction));
            updateFutureTransactionStyle(row, row.getItem());
            return row;
        });
    }

    private void updateFutureTransactionStyle(TableRow<Transaction> row, Transaction transaction) {
        row.getStyleClass().remove("future-transaction-row");
        if (isFutureTransaction(transaction)) {
            row.getStyleClass().add("future-transaction-row");
        }
    }

    private boolean isFutureTransaction(Transaction transaction) {
        return transaction != null && transaction.getBookingDate() != null
                && transaction.getBookingDate().isAfter(LocalDate.now());
    }

    private void setupUnviewedTransactionRows() {
        var existingRowFactory = transactionTable.getRowFactory();
        transactionTable.setRowFactory(table -> {
            TableRow<Transaction> row = existingRowFactory == null
                    ? new TableRow<>() : existingRowFactory.call(table);
            row.itemProperty().addListener((observable, oldTransaction, newTransaction) ->
                    updateUnviewedTransactionStyle(row, newTransaction));
            updateUnviewedTransactionStyle(row, row.getItem());
            return row;
        });
    }

    private void updateUnviewedTransactionStyle(TableRow<Transaction> row, Transaction transaction) {
        row.getStyleClass().remove("new-transaction-row");
        if (transaction != null && transaction.getId() != null
                && !settledTransactionIds.isSettled(transaction.getId())) {
            row.getStyleClass().add("new-transaction-row");
        }
    }

    private void installSelectionListener() {
        transactionTable.getSelectionModel().selectedItemProperty().addListener((observable, oldTransaction,
                                                                                newTransaction) -> {
            if (oldTransaction != null && oldTransaction.getId() != null) {
                settledTransactionIds.settle(oldTransaction.getId());
            }
            Platform.runLater(transactionTable::refresh);
        });
    }

    private void setupDateColumn(TableColumn<Transaction, LocalDate> column,
                                 Function<Transaction, LocalDate> dateFor,
                                 DateTimeFormatter dateFormatter) {
        column.setCellValueFactory(cell -> new SimpleObjectProperty<>(dateFor.apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(dateFormatter));
            }
        });
    }

    private TableCell<Transaction, String> createAssignmentCell(java.util.function.Consumer<Node> openAssignment) {
        TableCell<Transaction, String> cell = new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (getGraphic() instanceof HBox previous) previous.prefWidthProperty().unbind();
                if (empty) {
                    setGraphic(null);
                    return;
                }
                Label value = new Label(item == null ? "" : item);
                value.setMaxWidth(Double.MAX_VALUE);
                Button assignmentButton = createAssignmentButton(
                        this, "picker.category.accessible", openAssignment);
                SearchableDropdownPicker.installToggleHandler(value, assignmentButton, assignmentButton::fire);
                HBox control = new HBox(4, value, assignmentButton);
                control.getStyleClass().add("assignment-cell");
                control.prefWidthProperty().bind(Bindings.max(0, categoryColumn.widthProperty().subtract(12)));
                HBox.setHgrow(value, javafx.scene.layout.Priority.ALWAYS);
                setGraphic(control);
            }
        };
        return cell;
    }

    private TableCell<Transaction, String> createLabelChipCell(java.util.function.Consumer<Node> openAssignment) {
        TableCell<Transaction, String> cell = new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (getGraphic() instanceof HBox previous) previous.prefWidthProperty().unbind();
                Transaction transaction = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || transaction == null) {
                    setGraphic(null);
                    return;
                }

                List<org.fintrax.model.Label> labels = transaction.getLabelIds() == null ? List.of()
                        : transaction.getLabelIds().stream()
                        .map(labelService::getLabel)
                        .flatMap(Optional::stream)
                        .toList();
                HBox chips = LabelChipFactory.createAll(labels);
                Button assignmentButton = createAssignmentButton(
                        this, "picker.labels.accessible", openAssignment);
                SearchableDropdownPicker.installToggleHandler(chips, assignmentButton, assignmentButton::fire);
                HBox control = new HBox(4, chips, assignmentButton);
                control.getStyleClass().add("assignment-cell");
                control.prefWidthProperty().bind(Bindings.max(0, labelsColumn.widthProperty().subtract(12)));
                HBox.setHgrow(chips, javafx.scene.layout.Priority.ALWAYS);
                setGraphic(control);
            }
        };
        return cell;
    }

    private Button createAssignmentButton(TableCell<Transaction, String> cell, String accessibleTextKey,
                                          java.util.function.Consumer<Node> openAssignment) {
        Button button = new Button("▾");
        button.setAccessibleText(I18n.get(accessibleTextKey));
        button.getStyleClass().add("assignment-picker-button");
        button.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (SearchableDropdownPicker.dismissIfActiveAt(button)) event.consume();
        });
        button.setOnAction(event -> {
            if (!cell.isEmpty() && !transactionTable.getSelectionModel().isSelected(cell.getIndex())) {
                transactionTable.getSelectionModel().clearAndSelect(cell.getIndex());
            }
            openAssignment.accept(button);
        });
        return button;
    }

    private void loadTransactions() {
        Optional<TransactionFilter> criteria = transactionFilterPanelController.getCriteria();
        if (criteria.isEmpty()) return;

        settledTransactionIds.clear();
        List<Transaction> filtered = transactionService.filterTransactions(criteria.get());

        tableData.setAll(filtered);
        transactionWorkspaceController.setTableItems(tableData);
        if (!transactionTable.getSortOrder().isEmpty()) {
            transactionTable.sort();
        }
    }

    private void reloadTransactionsKeepingSelection() {
        Set<Long> selectedIds = getSelectedTransactions().stream()
                .map(Transaction::getId)
                .collect(Collectors.toSet());

        loadTransactions();

        for (int index = 0; index < tableData.size(); index++) {
            if (selectedIds.contains(tableData.get(index).getId())) {
                transactionTable.getSelectionModel().select(index);
            }
        }
    }

    @FXML
    private void onBulkCategory(Node anchor) {
        transactionTableActions.setCategory(
                transactionTable, anchor, this::reloadTransactionsKeepingSelection);
    }

    @FXML
    private void onSetLabels(Node anchor) {
        transactionTableActions.setLabels(
                transactionTable, anchor, this::reloadTransactionsKeepingSelection);
    }

    private void createRuleFromTransaction(Transaction transaction) {
        CreateRuleFromTransactionDialog.show(rootPane.getScene().getWindow(), transaction, ruleService, viewLoader);
    }

    private List<Transaction> getSelectedTransactions() {
        return new ArrayList<>(transactionTable.getSelectionModel().getSelectedItems());
    }

    private void setupColumnMenu() {
        TableColumnVisibilityMenu.install(
                transactionTable, columnMenuButton, COLUMN_CONFIG_KEY, settingsService, columnConfigurations());
    }

    private List<TableColumnConfig> columnConfigurations() {
        return List.of(
                new TableColumnConfig("date", bookingDateColumn),
                new TableColumnConfig("valueDate", valueDateColumn),
                new TableColumnConfig("payee", payeeColumn),
                new TableColumnConfig("account", accountColumn),
                new TableColumnConfig("purpose", purposeColumn),
                new TableColumnConfig("amount", amountColumn),
                new TableColumnConfig("balance", balanceColumn),
                new TableColumnConfig("category", categoryColumn),
                new TableColumnConfig("labels", labelsColumn));
    }
}
