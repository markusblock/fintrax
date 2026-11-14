package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.SplitPane;
import org.fintrax.model.Transaction;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

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

    public void attachDetails(TableColumn<?, ?> categoryColumn, TableColumn<?, ?> labelsColumn) {
        transactionDetailsPanelController.attach(
                transactionTable, transactionSplitPane, categoryColumn, labelsColumn);
    }

    public void toggleDetails() {
        transactionDetailsPanelController.toggleDetails();
    }
}
