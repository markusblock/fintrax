package org.fintrax.ui.control;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.SettingsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Configures the standard transaction columns shared by RulesView and ClassificationView. */
public final class TransactionTableSetup {
    // Keep these indices aligned with the shared transaction columns in both view FXML files.
    private static final int BOOKING_DATE_COLUMN = 0;
    private static final int VALUE_DATE_COLUMN = 1;
    private static final int PAYEE_COLUMN = 2;
    private static final int ACCOUNT_COLUMN = 3;
    private static final int PURPOSE_COLUMN = 4;
    private static final int AMOUNT_COLUMN = 5;
    private static final int CATEGORY_COLUMN = 6;
    private static final int LABELS_COLUMN = 7;
    private static final String AMOUNT_POSITIVE_STYLE = "amount-positive";
    private static final String AMOUNT_NEGATIVE_STYLE = "amount-negative";

    private final TableView<Transaction> table;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;

    public TransactionTableSetup(TableView<Transaction> table, AccountService accountService,
                                 CategoryService categoryService, LabelService labelService) {
        this.table = table;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
    }

    public void configure() {
        TableViewResizePolicy.fitColumnsAndAllowHorizontalScrolling(table);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        setupDateColumn(bookingDateColumn(), Transaction::getBookingDate, dateFormatter);
        setupDateColumn(valueDateColumn(), Transaction::getValueDate, dateFormatter);
        payeeColumn().setCellValueFactory(cell -> new SimpleStringProperty(
                Objects.toString(cell.getValue().getPayeeDisplay() != null
                        ? cell.getValue().getPayeeDisplay() : cell.getValue().getOriginalPayee(), "")));
        accountColumn().setCellValueFactory(cell -> {
            Long accountId = cell.getValue().getAccountId();
            return new SimpleStringProperty(accountId == null ? "" : AccountDisplay.name(
                    accountService.getAccount(accountId).orElse(null)));
        });
        purposeColumn().setCellValueFactory(cell ->
                new SimpleStringProperty(SingleLineText.normalize(cell.getValue().getPurpose())));
        setupAmountColumn(amountColumn());
        categoryColumn().setCellValueFactory(cell -> {
            Long categoryId = cell.getValue().getCategoryId();
            return new SimpleStringProperty(categoryId == null ? "" : categoryService.getCategory(categoryId)
                    .map(Category::getName).orElse(""));
        });
        labelsColumn().setCellValueFactory(cell -> {
            Set<Long> labelIds = cell.getValue().getLabelIds();
            if (labelIds == null || labelIds.isEmpty()) return new SimpleStringProperty("");
            return new SimpleStringProperty(labelIds.stream()
                    .map(labelService::getLabel)
                    .flatMap(Optional::stream)
                    .map(org.fintrax.model.Label::getName)
                    .collect(Collectors.joining(", ")));
        });
        bookingDateColumn().setSortable(true);
        valueDateColumn().setSortable(true);
        payeeColumn().setSortable(true);
        accountColumn().setSortable(true);
        purposeColumn().setSortable(true);
        amountColumn().setSortable(true);
        categoryColumn().setSortable(true);
        labelsColumn().setSortable(true);
    }

    public void installColumnMenu(String configKey, SettingsService settingsService) {
        installColumnMenu(configKey, settingsService, List.of());
    }

    public void installColumnMenu(String configKey, SettingsService settingsService,
                                  List<TableColumnConfig> additionalColumns) {
        MenuButton columnMenuButton = table.getColumns().stream()
                .map(TableColumn::getGraphic)
                .filter(MenuButton.class::isInstance)
                .map(MenuButton.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction table must have a column menu button"));
        List<TableColumnConfig> columns = new ArrayList<>(List.of(
                new TableColumnConfig("date", bookingDateColumn()),
                new TableColumnConfig("valueDate", valueDateColumn()),
                new TableColumnConfig("payee", payeeColumn()),
                new TableColumnConfig("account", accountColumn()),
                new TableColumnConfig("purpose", purposeColumn()),
                new TableColumnConfig("amount", amountColumn()),
                new TableColumnConfig("category", categoryColumn()),
                new TableColumnConfig("labels", labelsColumn())));
        columns.addAll(additionalColumns);
        TableColumnVisibilityMenu.install(table, columnMenuButton, configKey, settingsService, columns);
    }

    public TableColumn<Transaction, String> categoryColumn() {
        return column(CATEGORY_COLUMN);
    }

    public TableColumn<Transaction, String> labelsColumn() {
        return column(LABELS_COLUMN);
    }

    private TableColumn<Transaction, LocalDate> bookingDateColumn() {
        return column(BOOKING_DATE_COLUMN);
    }

    private TableColumn<Transaction, LocalDate> valueDateColumn() {
        return column(VALUE_DATE_COLUMN);
    }

    private TableColumn<Transaction, String> payeeColumn() {
        return column(PAYEE_COLUMN);
    }

    private TableColumn<Transaction, String> accountColumn() {
        return column(ACCOUNT_COLUMN);
    }

    private TableColumn<Transaction, String> purposeColumn() {
        return column(PURPOSE_COLUMN);
    }

    private TableColumn<Transaction, BigDecimal> amountColumn() {
        return column(AMOUNT_COLUMN);
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<Transaction, T> column(int index) {
        return (TableColumn<Transaction, T>) table.getColumns().get(index);
    }

    private void setupDateColumn(TableColumn<Transaction, LocalDate> dateColumn,
                                 Function<Transaction, LocalDate> dateFor,
                                 DateTimeFormatter dateFormatter) {
        dateColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(dateFor.apply(cell.getValue())));
        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(dateFormatter));
            }
        });
    }

    private void setupAmountColumn(TableColumn<Transaction, BigDecimal> amountColumn) {
        amountColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().getAmount()));
        amountColumn.setCellFactory(column -> new TableCell<>() {
            {
                setAlignment(Pos.CENTER_RIGHT);
            }

            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(AMOUNT_POSITIVE_STYLE, AMOUNT_NEGATIVE_STYLE);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.2f", item));
                    getStyleClass().add(item.signum() < 0 ? AMOUNT_NEGATIVE_STYLE : AMOUNT_POSITIVE_STYLE);
                }
            }
        });
    }
}
