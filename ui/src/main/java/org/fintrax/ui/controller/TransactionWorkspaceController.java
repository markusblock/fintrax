package org.fintrax.ui.controller;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.SplitPane;
import org.fintrax.config.I18n;
import org.fintrax.model.Transaction;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Shared filter, transaction table, and transaction details workspace used by transaction views. */
@Component
@Scope("prototype")
public class TransactionWorkspaceController {
    @FXML
    private TransactionFilterPanelController transactionFilterPanelController;
    @FXML
    private TableView<Transaction> transactionTable;
    @FXML
    private SplitPane transactionSplitPane;
    @FXML
    private TransactionDetailsPanelController transactionDetailsPanelController;
    @FXML
    private Label workspaceTitle;
    @FXML
    private Label transactionCountValue;
    @FXML
    private Label transactionTotalAmount;
    @FXML
    private Button detailToggleButton;

    private ObservableList<Transaction> observedTransactions;
    private final ListChangeListener<Transaction> transactionItemsListener = change -> updateSummary();

    @FXML
    public void initialize() {
        observeItems(transactionTable.getItems());
    }

    public TableView<Transaction> table() {
        return transactionTable;
    }

    public TransactionFilterPanelController filterPanel() {
        return transactionFilterPanelController;
    }

    public void setTableId(String id) {
        transactionTable.setId(id);
    }

    public void setTableColumns(List<TableColumn<Transaction, ?>> columns) {
        transactionTable.getColumns().setAll(columns);
    }

    public void setTableItems(ObservableList<Transaction> items) {
        transactionTable.setItems(items);
        observeItems(items);
    }

    public void attachDetails(Set<TableColumn<?, ?>> interactiveColumns) {
        transactionDetailsPanelController.attach(
                transactionTable, transactionSplitPane, interactiveColumns);
    }

    public void toggleDetails() {
        transactionDetailsPanelController.toggleDetails();
    }

    public void setHeadlineAsPageTitle() {
        workspaceTitle.getStyleClass().remove("section-heading");
        workspaceTitle.getStyleClass().add("page-title");
    }

    public void setDetailToggleVisible(boolean visible) {
        detailToggleButton.setVisible(visible);
        detailToggleButton.setManaged(visible);
    }

    @FXML
    private void onToggleDetails() {
        toggleDetails();
    }

    private void observeItems(ObservableList<Transaction> items) {
        if (observedTransactions != null) observedTransactions.removeListener(transactionItemsListener);
        observedTransactions = items;
        if (observedTransactions != null) observedTransactions.addListener(transactionItemsListener);
        updateSummary();
    }

    private void updateSummary() {
        int count = observedTransactions == null ? 0 : observedTransactions.size();
        String countKey = count == 1
                ? "transactionWorkspace.count.one" : "transactionWorkspace.count.many";
        transactionCountValue.setText(I18n.get(countKey, count));

        BigDecimal total = observedTransactions == null ? BigDecimal.ZERO : observedTransactions.stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        transactionTotalAmount.setText(String.format(I18n.getCurrentLocale(), "%,.2f EUR", total));
        transactionTotalAmount.getStyleClass().removeAll("amount-positive", "amount-negative");
        transactionTotalAmount.getStyleClass().add(total.signum() < 0 ? "amount-negative" : "amount-positive");
    }
}
