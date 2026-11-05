package org.fintrax.ui.control;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class TransactionTableSetup {
    private static final String AMOUNT_POSITIVE_STYLE = "amount-positive";
    private static final String AMOUNT_NEGATIVE_STYLE = "amount-negative";

    private TransactionTableSetup() {}

    /** Configures the shared transaction columns; views may add specialized cell factories afterward. */
    public static void configure(TableView<Transaction> table,
                                 TableColumn<Transaction, LocalDate> dateColumn,
                                 TableColumn<Transaction, String> payeeColumn,
                                 TableColumn<Transaction, String> accountColumn,
                                 TableColumn<Transaction, String> purposeColumn,
                                 TableColumn<Transaction, BigDecimal> amountColumn,
                                 TableColumn<Transaction, String> categoryColumn,
                                 TableColumn<Transaction, String> labelsColumn,
                                 AccountService accountService, CategoryService categoryService,
                                 LabelService labelService) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setupDateColumn(dateColumn);
        payeeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                Objects.toString(cell.getValue().getPayeeDisplay() != null
                        ? cell.getValue().getPayeeDisplay() : cell.getValue().getOriginalPayee(), "")));
        accountColumn.setCellValueFactory(cell -> {
            Long accountId = cell.getValue().getAccountId();
            return new SimpleStringProperty(accountId == null ? "" : AccountDisplay.name(
                    accountService.getAccount(accountId).orElse(null)));
        });
        purposeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(SingleLineText.normalize(cell.getValue().getPurpose())));
        setupAmountColumn(amountColumn);
        categoryColumn.setCellValueFactory(cell -> {
            Long categoryId = cell.getValue().getCategoryId();
            return new SimpleStringProperty(categoryId == null ? "" : categoryService.getCategory(categoryId)
                    .map(Category::getName).orElse(""));
        });
        labelsColumn.setCellValueFactory(cell -> {
            Set<Long> labelIds = cell.getValue().getLabelIds();
            if (labelIds == null || labelIds.isEmpty()) return new SimpleStringProperty("");
            return new SimpleStringProperty(labelIds.stream()
                    .map(labelService::getLabel)
                    .flatMap(Optional::stream)
                    .map(org.fintrax.model.Label::getName)
                    .collect(Collectors.joining(", ")));
        });
        dateColumn.setSortable(true);
        payeeColumn.setSortable(true);
        accountColumn.setSortable(true);
        purposeColumn.setSortable(true);
        amountColumn.setSortable(true);
        categoryColumn.setSortable(true);
        labelsColumn.setSortable(true);
    }

    private static void setupDateColumn(TableColumn<Transaction, LocalDate> dateColumn) {
        dateColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getBookingDate()));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(dateFormatter));
            }
        });
    }

    private static void setupAmountColumn(TableColumn<Transaction, BigDecimal> amountColumn) {
        amountColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getAmount()));
        amountColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(AMOUNT_POSITIVE_STYLE, AMOUNT_NEGATIVE_STYLE);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.2f EUR", item));
                    getStyleClass().add(item.signum() < 0 ? AMOUNT_NEGATIVE_STYLE : AMOUNT_POSITIVE_STYLE);
                }
            }
        });
    }
}
