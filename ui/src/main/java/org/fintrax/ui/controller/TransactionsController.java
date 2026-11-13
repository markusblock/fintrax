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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
    private static final String BOOKED_STYLE_CLASS = "booked";
    private static final String PENDING_STYLE_CLASS = "pending";
    private static final double TABLE_DIVIDER_WITH_DETAILS = 0.45;
    private static final double TABLE_DIVIDER_WITHOUT_DETAILS = 1.0;
    private static final double MIN_TABLE_HEIGHT_WITH_DETAILS = 150;
    private static final double DETAIL_VIEWPORT_HEIGHT_BUFFER = 8;

    @FXML
    private BorderPane rootPane;
    @FXML
    private TransactionFilterPanelController transactionFilterPanelController;
    @FXML
    private SplitPane splitPane;
    @FXML
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
    private VBox detailPanel;
    @FXML
    private GridPane detailForm;
    @FXML
    private ScrollPane detailScrollPane;
    @FXML
    private Label accountValue;
    @FXML
    private Label bookingDateValue;
    @FXML
    private Label valueDateValue;
    @FXML
    private Label originalPayeeValue;
    @FXML
    private Label payeeValue;
    @FXML
    private Label purposeValue;
    @FXML
    private Label categoryValue;
    @FXML
    private FlowPane labelsValue;
    @FXML
    private Label noteValue;
    @FXML
    private Label amountValue;
    @FXML
    private Label balanceValue;
    @FXML
    private Label transactionTypeValue;
    @FXML
    private Label bookingStatusValue;
    @FXML
    private Label endToEndIdValue;
    @FXML
    private Label mandateIdValue;
    @FXML
    private Label creditorIdValue;

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final SettingsService settingsService;
    private final RuleService ruleService;
    private final ViewLoader viewLoader;
    private final TransactionTableActions transactionTableActions;

    private final ObservableList<Transaction> tableData = FXCollections.observableArrayList();
    private boolean detailVisible = false;

    public TransactionsController(TransactionService transactionService, AccountService accountService,
                                  CategoryService categoryService, LabelService labelService,
                                  SettingsService settingsService, RuleService ruleService,
                                  ViewLoader viewLoader, TransactionTableActions transactionTableActions) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.settingsService = settingsService;
        this.ruleService = ruleService;
        this.viewLoader = viewLoader;
        this.transactionTableActions = transactionTableActions;
    }

    @FXML
    public void initialize() {
        log.info("TransactionsController initialized");
        transactionTableActions.installContextMenu(
                transactionTable, this::reloadTransactionsKeepingSelection, this::createRuleFromTransaction);
        setupTableColumns();
        TableCellTooltips.install(transactionTable);
        transactionFilterPanelController.setCollapsible(true);
        transactionFilterPanelController.restoreForView(SessionViewState.TRANSACTIONS_VIEW);
        transactionFilterPanelController.setOnFiltersChanged(this::loadTransactions);
        loadTransactions();
        setupColumnMenu();
        clearDetail();

        transactionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadDetail(newVal);
            } else {
                clearDetail();
            }
        });
        transactionTable.addEventFilter(MouseEvent.MOUSE_CLICKED, this::openDetailsOnDoubleClick);
        splitPane.heightProperty().addListener((observable, oldHeight, newHeight) -> {
            if (detailVisible) Platform.runLater(this::sizeDetailPanelToContent);
        });
        detailScrollPane.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            if (detailVisible) Platform.runLater(this::sizeDetailPanelToContent);
        });
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

        balanceColumn.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getBalanceAfter()));
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

    private void openDetailsOnDoubleClick(MouseEvent event) {
        if (event.getClickCount() != 2 || !(event.getTarget() instanceof Node target)) return;

        TableCell<?, ?> clickedCell = findTableCell(target);
        if (clickedCell == null || clickedCell.isEmpty()) return;
        TableColumn<?, ?> clickedColumn = clickedCell.getTableColumn();
        if (clickedColumn == null || clickedColumn == categoryColumn || clickedColumn == labelsColumn
                || clickedColumn.getGraphic() instanceof MenuButton) {
            return;
        }

        int rowIndex = clickedCell.getIndex();
        if (rowIndex < 0 || rowIndex >= transactionTable.getItems().size()) return;
        transactionTable.getSelectionModel().clearAndSelect(rowIndex);
        setDetailVisible(true);
    }

    private TableCell<?, ?> findTableCell(Node target) {
        for (Node current = target; current != null; current = current.getParent()) {
            if (current instanceof TableCell<?, ?> cell) return cell;
        }
        return null;
    }

    private void loadTransactions() {
        Optional<TransactionFilter> criteria = transactionFilterPanelController.getCriteria();
        if (criteria.isEmpty()) return;

        List<Transaction> filtered = transactionService.filterTransactions(criteria.get());

        tableData.setAll(filtered);
        transactionTable.setItems(tableData);
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
    private void onToggleDetail() {
        setDetailVisible(!detailVisible);
    }

    private void setDetailVisible(boolean visible) {
        detailVisible = visible;
        detailPanel.setVisible(detailVisible);
        detailPanel.setManaged(detailVisible);
        if (detailVisible) {
            splitPane.setDividerPosition(0, TABLE_DIVIDER_WITH_DETAILS);
            Platform.runLater(this::sizeDetailPanelToContent);
        } else {
            splitPane.setDividerPosition(0, TABLE_DIVIDER_WITHOUT_DETAILS);
        }
    }

    private void sizeDetailPanelToContent() {
        if (!detailVisible) return;
        double availableHeight = splitPane.getHeight();
        if (availableHeight <= 0) return;

        double contentWidth = detailScrollPane.getViewportBounds().getWidth();
        if (contentWidth <= 0) contentWidth = splitPane.getWidth() - 40;
        if (contentWidth <= 0) return;

        detailScrollPane.setPrefViewportHeight(
                detailForm.prefHeight(contentWidth) + DETAIL_VIEWPORT_HEIGHT_BUFFER);
        splitPane.requestLayout();
        Platform.runLater(() -> {
            if (!detailVisible) return;
            double maxDetailHeight = Math.max(0, splitPane.getHeight() - MIN_TABLE_HEIGHT_WITH_DETAILS);
            double detailHeight = Math.min(detailPanel.prefHeight(-1), maxDetailHeight);
            if (detailHeight > 0 && splitPane.getHeight() > 0) {
                splitPane.setDividerPosition(0, 1.0 - detailHeight / splitPane.getHeight());
            }
        });
    }

    private void loadDetail(Transaction transaction) {
        BankAccount account = transaction.getAccountId() == null ? null
                : accountService.getAccount(transaction.getAccountId()).orElse(null);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        String currency = accountCurrency(account);

        setAmountValue(amountValue, transaction.getAmount(), currency);
        setAmountValue(balanceValue, transaction.getBalanceAfter(), currency);
        setPropertyText(categoryValue, categoryName(transaction.getCategoryId()));
        setDetailLabels(transaction.getLabelIds());
        setPropertyText(noteValue, transaction.getNote());
        setPropertyText(bookingDateValue, formatDate(transaction.getBookingDate(), dateFormatter));
        setPropertyText(valueDateValue, formatDate(transaction.getValueDate(), dateFormatter));
        setPropertyText(accountValue, AccountDisplay.name(account));
        setPropertyText(transactionTypeValue, transaction.getTransactionType());
        setPropertyText(payeeValue, transaction.getPayeeDisplay());
        setPropertyText(originalPayeeValue, transaction.getOriginalPayee());
        setPropertyText(purposeValue, transaction.getPurpose());
        boolean booked = transaction.isBooked();
        bookingStatusValue.setText(I18n.get(booked ? "detail.status.booked" : "detail.status.pending"));
        bookingStatusValue.getStyleClass().removeAll(BOOKED_STYLE_CLASS, PENDING_STYLE_CLASS);
        bookingStatusValue.getStyleClass().add(booked ? BOOKED_STYLE_CLASS : PENDING_STYLE_CLASS);
        setPropertyText(endToEndIdValue, transaction.getEndToEndId());
        setPropertyText(mandateIdValue, transaction.getMandateId());
        setPropertyText(creditorIdValue, transaction.getCreditorId());
    }

    private void clearDetail() {
        labelsValue.getChildren().clear();
        bookingStatusValue.getStyleClass().removeAll(BOOKED_STYLE_CLASS, PENDING_STYLE_CLASS);
        amountValue.getStyleClass().removeAll(AMOUNT_POSITIVE_STYLE_CLASS, AMOUNT_NEGATIVE_STYLE_CLASS);
        balanceValue.getStyleClass().removeAll(AMOUNT_POSITIVE_STYLE_CLASS, AMOUNT_NEGATIVE_STYLE_CLASS);
        for (Label value : List.of(accountValue, bookingDateValue, valueDateValue, originalPayeeValue,
                payeeValue, purposeValue, categoryValue, noteValue, amountValue, balanceValue,
                transactionTypeValue, bookingStatusValue, endToEndIdValue, mandateIdValue, creditorIdValue)) {
            value.setText("");
        }
    }

    private void setPropertyText(Label value, String text) {
        value.setText(text == null ? "" : text);
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) return "";
        return categoryService.getCategory(categoryId).map(Category::getName).orElse("");
    }

    private void setDetailLabels(Set<Long> labelIds) {
        labelsValue.getChildren().clear();
        List<org.fintrax.model.Label> labels = labelIds == null ? List.of() : labelIds.stream()
                .map(labelService::getLabel)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(org.fintrax.model.Label::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (labels.isEmpty()) return;
        labels.forEach(label -> labelsValue.getChildren().add(LabelChipFactory.create(label)));
    }

    private String accountCurrency(BankAccount account) {
        return account == null || account.getCurrency() == null || account.getCurrency().isBlank()
                ? "EUR" : account.getCurrency();
    }

    private String formatDate(LocalDate date, DateTimeFormatter formatter) {
        return date == null ? "" : date.format(formatter);
    }

    private String formatAmount(BigDecimal amount, String currency) {
        return amount == null ? "" : String.format(I18n.getCurrentLocale(), "%,.2f %s", amount, currency);
    }

    private void setAmountValue(Label value, BigDecimal amount, String currency) {
        String formattedAmount = formatAmount(amount, currency);
        value.setText(formattedAmount);
        value.getStyleClass().removeAll(AMOUNT_POSITIVE_STYLE_CLASS, AMOUNT_NEGATIVE_STYLE_CLASS);
        if (amount != null) {
            value.getStyleClass().add(amount.signum() < 0
                    ? AMOUNT_NEGATIVE_STYLE_CLASS : AMOUNT_POSITIVE_STYLE_CLASS);
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
