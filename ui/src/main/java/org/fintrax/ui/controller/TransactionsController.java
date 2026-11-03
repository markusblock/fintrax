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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.*;
import org.fintrax.ui.control.LabelChipFactory;
import org.fintrax.ui.control.SingleLineText;
import org.fintrax.ui.control.TableColumnConfig;
import org.fintrax.ui.control.TableColumnVisibilityMenu;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
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
    private static final double TABLE_DIVIDER_WITH_DETAILS = 0.7;
    private static final double TABLE_DIVIDER_WITHOUT_DETAILS = 1.0;

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
    private TableColumn<Transaction, LocalDate> dateColumn;
    @FXML
    private TableColumn<Transaction, String> payeeColumn;
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
    private StackPane bookingStatusCell;
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
    private final TransactionTableActions transactionTableActions;

    private final ObservableList<Transaction> tableData = FXCollections.observableArrayList();
    private final Map<Integer, Node> detailRowSeparators = new HashMap<>();
    private boolean detailVisible = false;

    public TransactionsController(TransactionService transactionService, AccountService accountService,
                                  CategoryService categoryService, LabelService labelService,
                                  SettingsService settingsService, TransactionTableActions transactionTableActions) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.settingsService = settingsService;
        this.transactionTableActions = transactionTableActions;
    }

    @FXML
    public void initialize() {
        log.info("TransactionsController initialized");
        transactionTableActions.installContextMenu(
                transactionTable, rootPane, this::reloadTransactionsKeepingSelection);
        setupTableColumns();
        transactionFilterPanelController.setCollapsible(true);
        transactionFilterPanelController.setOnFiltersChanged(this::loadTransactions);
        loadTransactions();
        setupColumnMenu();
        detailRowSeparators.clear();
        detailForm.lookupAll(".detail-row-separator").forEach(separator ->
                detailRowSeparators.put(GridPane.getRowIndex(separator), separator));
        clearDetail();

        transactionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadDetail(newVal);
            } else {
                clearDetail();
            }
        });
        Platform.runLater(rootPane::applyCss);
    }

    private void setupTableColumns() {
        transactionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        dateColumn.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getBookingDate()));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        dateColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(dateFormatter));
            }
        });

        payeeColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getPayeeDisplay() != null ? cd.getValue().getPayeeDisplay() : cd.getValue().getOriginalPayee()));

        purposeColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                SingleLineText.normalize(cd.getValue().getPurpose())));

        amountColumn.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getAmount()));
        amountColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%,.2f EUR", item.doubleValue()));
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
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.2f EUR", item.doubleValue()));
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

    private TableCell<Transaction, String> createAssignmentCell(Runnable openAssignmentDialog) {
        TableCell<Transaction, String> cell = new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        installAssignmentHandler(cell, openAssignmentDialog);
        return cell;
    }

    private TableCell<Transaction, String> createLabelChipCell(Runnable openAssignmentDialog) {
        TableCell<Transaction, String> cell = new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (getGraphic() instanceof HBox previous) previous.prefWidthProperty().unbind();
                Transaction transaction = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || transaction == null || transaction.getLabelIds() == null) {
                    setGraphic(null);
                    return;
                }

                List<org.fintrax.model.Label> labels = transaction.getLabelIds().stream()
                        .map(labelService::getLabel)
                        .flatMap(Optional::stream)
                        .toList();
                HBox chips = LabelChipFactory.createAll(labels);
                chips.prefWidthProperty().bind(Bindings.max(0, labelsColumn.widthProperty().subtract(12)));
                setGraphic(chips.getChildren().isEmpty() ? null : chips);
            }
        };
        installAssignmentHandler(cell, openAssignmentDialog);
        return cell;
    }

    private void installAssignmentHandler(TableCell<Transaction, String> cell,
                                          Runnable openAssignmentDialog) {
        cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2 && !cell.isEmpty()) {
                if (!transactionTable.getSelectionModel().isSelected(cell.getIndex())) {
                    transactionTable.getSelectionModel().clearAndSelect(cell.getIndex());
                }
                openAssignmentDialog.run();
            }
        });
    }

    private void loadTransactions() {
        Optional<TransactionFilter> criteria = transactionFilterPanelController.getCriteria();
        if (criteria.isEmpty()) return;

        List<Transaction> filtered = transactionService.filterTransactions(criteria.get());

        tableData.setAll(filtered);
        transactionTable.setItems(tableData);
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
        detailVisible = !detailVisible;
        detailPanel.setVisible(detailVisible);
        detailPanel.setManaged(detailVisible);
        splitPane.setDividerPosition(0, detailVisible
                ? TABLE_DIVIDER_WITH_DETAILS
                : TABLE_DIVIDER_WITHOUT_DETAILS);
    }

    private void loadDetail(Transaction transaction) {
        BankAccount account = transaction.getAccountId() == null ? null
                : accountService.getAccount(transaction.getAccountId()).orElse(null);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        String currency = accountCurrency(account);

        boolean hasAmount = setAmountValue(amountValue, transaction.getAmount(), currency);
        boolean hasBalance = setAmountValue(balanceValue, transaction.getBalanceAfter(), currency);
        boolean hasCategory = setPropertyText(categoryValue, categoryName(transaction.getCategoryId()));
        boolean hasLabels = setDetailLabels(transaction.getLabelIds());
        boolean hasNote = setPropertyText(noteValue, transaction.getNote());
        boolean hasBookingDate = setPropertyText(
                bookingDateValue, formatDate(transaction.getBookingDate(), dateFormatter));
        boolean hasValueDate = setPropertyText(
                valueDateValue, formatDate(transaction.getValueDate(), dateFormatter));
        boolean hasAccount = setPropertyText(accountValue, accountName(account));
        boolean hasTransactionType = setPropertyText(transactionTypeValue, transaction.getTransactionType());
        boolean hasPayee = setPropertyText(payeeValue, transaction.getPayeeDisplay());
        boolean hasOriginalPayee = setPropertyText(originalPayeeValue, transaction.getOriginalPayee());
        boolean hasPurpose = setPropertyText(purposeValue, transaction.getPurpose());
        boolean booked = transaction.isBooked();
        bookingStatusValue.setText(I18n.get(booked ? "detail.status.booked" : "detail.status.pending"));
        bookingStatusValue.getStyleClass().removeAll(BOOKED_STYLE_CLASS, PENDING_STYLE_CLASS);
        bookingStatusValue.getStyleClass().add(booked ? BOOKED_STYLE_CLASS : PENDING_STYLE_CLASS);
        boolean hasEndToEndId = setPropertyText(endToEndIdValue, transaction.getEndToEndId());
        boolean hasMandateId = setPropertyText(mandateIdValue, transaction.getMandateId());
        boolean hasCreditorId = setPropertyText(creditorIdValue, transaction.getCreditorId());

        setDetailPair(0, "amountKey", amountValue, hasAmount, "balanceKey", balanceValue, hasBalance);
        setDetailPair(1, "categoryKey", categoryValue, hasCategory, "labelsKey", labelsValue, hasLabels);
        setDetailProperty(2, "noteKey", noteValue, hasNote);
        setDetailPair(3, "bookingDateKey", bookingDateValue, hasBookingDate,
                "valueDateKey", valueDateValue, hasValueDate);
        setDetailPair(4, "accountKey", accountValue, hasAccount,
                "transactionTypeKey", transactionTypeValue, hasTransactionType);
        setDetailPair(5, "payeeKey", payeeValue, hasPayee,
                "originalPayeeKey", originalPayeeValue, hasOriginalPayee);
        setDetailProperty(6, "purposeKey", purposeValue, hasPurpose);
        setDetailPair(7, "bookingStatusKey", bookingStatusCell, true,
                "endToEndIdKey", endToEndIdValue, hasEndToEndId);
        setDetailPair(8, "mandateIdKey", mandateIdValue, hasMandateId,
                "creditorIdKey", creditorIdValue, hasCreditorId);
    }

    private void clearDetail() {
        detailForm.getChildren().forEach(node -> setNodeVisible(node, false));
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

    private void setDetailPair(int row, String leftKeyId, Node leftValue, boolean showLeft,
                               String rightKeyId, Node rightValue, boolean showRight) {
        Label leftKey = propertyKey(leftKeyId);
        Label rightKey = propertyKey(rightKeyId);
        setNodeVisible(leftKey, showLeft);
        setNodeVisible(leftValue, showLeft);
        setNodeVisible(rightKey, showRight);
        setNodeVisible(rightValue, showRight);

        int rightColumn = showLeft ? 2 : 0;
        GridPane.setColumnIndex(rightKey, rightColumn);
        GridPane.setColumnIndex(rightValue, rightColumn + 1);
        setDetailRowVisible(row, showLeft || showRight);
    }

    private void setDetailProperty(int row, String keyId, Node value, boolean show) {
        setNodeVisible(propertyKey(keyId), show);
        setNodeVisible(value, show);
        setDetailRowVisible(row, show);
    }

    private Label propertyKey(String keyId) {
        Node node = detailForm.lookup("#" + keyId);
        if (!(node instanceof Label label)) {
            throw new IllegalStateException("Missing transaction property key: " + keyId);
        }
        return label;
    }

    private void setDetailRowVisible(int row, boolean show) {
        Node separator = detailRowSeparators.get(row);
        if (separator != null) setNodeVisible(separator, show);
    }

    private void setNodeVisible(Node node, boolean show) {
        node.setVisible(show);
        node.setManaged(show);
    }

    private boolean setPropertyText(Label value, String text) {
        value.setText(text == null ? "" : text);
        return text != null && !text.isBlank();
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) return "";
        return categoryService.getCategory(categoryId).map(Category::getName).orElse("");
    }

    private boolean setDetailLabels(Set<Long> labelIds) {
        labelsValue.getChildren().clear();
        List<org.fintrax.model.Label> labels = labelIds == null ? List.of() : labelIds.stream()
                .map(labelService::getLabel)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(org.fintrax.model.Label::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (labels.isEmpty()) return false;
        labels.forEach(label -> labelsValue.getChildren().add(LabelChipFactory.create(label)));
        return true;
    }

    private String accountName(BankAccount account) {
        if (account == null) return "";
        String bankName = account.getBankName() == null ? "" : account.getBankName().trim();
        String iban = account.getIban() == null ? "" : account.getIban().trim();
        String maskedIban = iban.length() < 8 ? iban
                : iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
        if (bankName.isBlank() && maskedIban.isBlank()) return "";
        if (bankName.isBlank()) return maskedIban;
        if (maskedIban.isBlank()) return bankName;
        return bankName + " (" + maskedIban + ")";
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

    private boolean setAmountValue(Label value, BigDecimal amount, String currency) {
        String formattedAmount = formatAmount(amount, currency);
        value.setText(formattedAmount);
        value.getStyleClass().removeAll(AMOUNT_POSITIVE_STYLE_CLASS, AMOUNT_NEGATIVE_STYLE_CLASS);
        if (amount != null) {
            value.getStyleClass().add(amount.signum() < 0
                    ? AMOUNT_NEGATIVE_STYLE_CLASS : AMOUNT_POSITIVE_STYLE_CLASS);
        }
        return !formattedAmount.isBlank();
    }

    @FXML
    private void onBulkCategory() {
        transactionTableActions.setCategory(
                transactionTable, rootPane, this::reloadTransactionsKeepingSelection);
    }

    @FXML
    private void onSetLabels() {
        transactionTableActions.setLabels(
                transactionTable, rootPane, this::reloadTransactionsKeepingSelection);
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
                new TableColumnConfig("date", dateColumn),
                new TableColumnConfig("payee", payeeColumn),
                new TableColumnConfig("purpose", purposeColumn),
                new TableColumnConfig("amount", amountColumn),
                new TableColumnConfig("balance", balanceColumn),
                new TableColumnConfig("category", categoryColumn),
                new TableColumnConfig("labels", labelsColumn));
    }
}
