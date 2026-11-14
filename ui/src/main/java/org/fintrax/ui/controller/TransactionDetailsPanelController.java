package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.fintrax.config.I18n;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.ui.control.AccountDisplay;
import org.fintrax.ui.control.LabelChipFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@Scope("prototype")
public class TransactionDetailsPanelController {
    private static final String AMOUNT_POSITIVE_STYLE_CLASS = "amount-positive";
    private static final String AMOUNT_NEGATIVE_STYLE_CLASS = "amount-negative";
    private static final String BOOKED_STYLE_CLASS = "booked";
    private static final String PENDING_STYLE_CLASS = "pending";
    private static final double TABLE_DIVIDER_WITH_DETAILS = 0.55;
    private static final double TABLE_DIVIDER_WITHOUT_DETAILS = 1.0;
    private static final double MIN_TABLE_HEIGHT_WITH_DETAILS = 150;
    private static final double DETAIL_VIEWPORT_HEIGHT_BUFFER = 8;

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

    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private TableView<Transaction> transactionTable;
    private SplitPane splitPane;
    private TableColumn<?, ?> categoryColumn;
    private TableColumn<?, ?> labelsColumn;
    private boolean detailVisible;

    public TransactionDetailsPanelController(AccountService accountService,
                                             CategoryService categoryService,
                                             LabelService labelService) {
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
    }

    public void attach(TableView<Transaction> table, SplitPane detailsSplitPane,
                       TableColumn<?, ?> transactionCategoryColumn, TableColumn<?, ?> transactionLabelsColumn) {
        transactionTable = table;
        splitPane = detailsSplitPane;
        categoryColumn = transactionCategoryColumn;
        labelsColumn = transactionLabelsColumn;
        transactionTable.getSelectionModel().selectedItemProperty().addListener((observable, oldTransaction,
                                                                                  newTransaction) -> {
            if (detailVisible) {
                if (newTransaction == null) clearDetail();
                else loadDetail(newTransaction);
            }
        });
        transactionTable.addEventFilter(MouseEvent.MOUSE_CLICKED, this::openDetailsOnDoubleClick);
        splitPane.heightProperty().addListener((observable, oldHeight, newHeight) -> {
            if (detailVisible) Platform.runLater(this::sizeDetailPanelToContent);
        });
        detailScrollPane.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            if (detailVisible) Platform.runLater(this::sizeDetailPanelToContent);
        });
        Platform.runLater(detailPanel::applyCss);
    }

    private void openDetailsOnDoubleClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2
                || !(event.getTarget() instanceof Node target)) return;

        TableCell<?, ?> clickedCell = findTableCell(target);
        if (clickedCell == null || clickedCell.isEmpty()) return;
        TableColumn<?, ?> clickedColumn = clickedCell.getTableColumn();
        if (clickedColumn == null || clickedColumn == categoryColumn || clickedColumn == labelsColumn
                || clickedColumn.getGraphic() instanceof MenuButton) return;

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

    @FXML
    private void onClose() {
        setDetailVisible(false);
    }

    public void toggleDetails() {
        setDetailVisible(!detailVisible);
    }

    private void setDetailVisible(boolean visible) {
        detailVisible = visible;
        detailPanel.setVisible(detailVisible);
        detailPanel.setManaged(detailVisible);
        if (detailVisible) {
            Transaction selected = transactionTable.getSelectionModel().getSelectedItem();
            if (selected == null) clearDetail();
            else loadDetail(selected);
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

        detailScrollPane.setPrefViewportHeight(detailForm.prefHeight(contentWidth) + DETAIL_VIEWPORT_HEIGHT_BUFFER);
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
        String currency = account == null || account.getCurrency() == null || account.getCurrency().isBlank()
                ? "EUR" : account.getCurrency();

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
        labels.forEach(label -> labelsValue.getChildren().add(LabelChipFactory.create(label)));
    }

    private String formatDate(LocalDate date, DateTimeFormatter formatter) {
        return date == null ? "" : date.format(formatter);
    }

    private String formatAmount(BigDecimal amount, String currency) {
        return amount == null ? "" : String.format(I18n.getCurrentLocale(), "%,.2f %s", amount, currency);
    }

    private void setAmountValue(Label value, BigDecimal amount, String currency) {
        value.setText(formatAmount(amount, currency));
        value.getStyleClass().removeAll(AMOUNT_POSITIVE_STYLE_CLASS, AMOUNT_NEGATIVE_STYLE_CLASS);
        if (amount != null) {
            value.getStyleClass().add(amount.signum() < 0
                    ? AMOUNT_NEGATIVE_STYLE_CLASS : AMOUNT_POSITIVE_STYLE_CLASS);
        }
    }
}
