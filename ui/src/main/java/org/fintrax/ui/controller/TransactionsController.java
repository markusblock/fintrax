package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.*;
import org.fintrax.store.StoreManager;
import org.fintrax.ui.control.LabelChipFactory;
import org.fintrax.ui.control.SearchableListPicker;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
public class TransactionsController {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String COLUMN_CONFIG_KEY = "transaction.columns";
    private static final double TABLE_DIVIDER_WITH_DETAILS = 0.7;
    private static final double TABLE_DIVIDER_WITHOUT_DETAILS = 1.0;

    @FXML
    private BorderPane rootPane;
    @FXML
    private ComboBox<String> accountFilter;
    @FXML
    private ComboBox<String> categoryFilter;
    @FXML
    private TextField searchField;
    @FXML
    private TextField minAmountField;
    @FXML
    private TextField maxAmountField;
    @FXML
    private ComboBox<TransactionDateFilters.Preset> datePresetFilter;
    @FXML
    private DatePicker fromDateFilter;
    @FXML
    private DatePicker toDateFilter;
    @FXML
    private SplitPane splitPane;
    @FXML
    private TableView<Transaction> transactionTable;
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
    private TextField payeeField;
    @FXML
    private Button detailCategoryButton;
    @FXML
    private Button detailLabelsButton;
    @FXML
    private TextArea noteArea;
    @FXML
    private Button detailSaveButton;

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final StoreManager storeManager;
    private final TransactionTableActions transactionTableActions;

    private final ObservableList<Transaction> tableData = FXCollections.observableArrayList();
    private boolean detailVisible = false;
    private boolean updatingDateFilters;
    private Long detailCategoryId;
    private Set<Long> detailLabelIds = new HashSet<>();

    public TransactionsController(AccountService accountService, TransactionService transactionService,
                                  CategoryService categoryService, LabelService labelService,
                                  StoreManager storeManager, TransactionTableActions transactionTableActions) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.storeManager = storeManager;
        this.transactionTableActions = transactionTableActions;
    }

    @FXML
    public void initialize() {
        log.info("TransactionsController initialized");
        transactionTableActions.installContextMenu(
                transactionTable, rootPane, this::reloadTransactionsKeepingSelection);
        setupTableColumns();
        setupDateFilters();
        loadFilterOptions();
        loadTransactions();
        restoreColumnConfig();

        transactionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadDetail(newVal);
            }
        });
        transactionTable.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<Transaction>) change -> updateDetailEditingState());
        updateDetailEditingState();
        detailCategoryButton.setOnAction(event -> chooseDetailCategory());
        detailLabelsButton.setOnAction(event -> chooseDetailLabels());
        Platform.runLater(rootPane::applyCss);
    }

    private void setupTableColumns() {
        transactionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        dateColumn.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getBookingDate()));
        dateColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(DATE_FMT));
            }
        });

        payeeColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getPayeeDisplay() != null ? cd.getValue().getPayeeDisplay() : cd.getValue().getOriginalPayee()));

        purposeColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPurpose()));

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
                    getStyleClass().removeAll("amount-positive", "amount-negative");
                    if (item.signum() < 0) {
                        getStyleClass().add("amount-negative");
                    } else {
                        getStyleClass().add("amount-positive");
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

    private void loadFilterOptions() {
        accountFilter.getItems().clear();
        accountFilter.getItems().add(I18n.get("filter.allAccounts"));
        for (BankAccount account : accountService.getAllAccounts()) {
            String display = (account.getBankName() != null ? account.getBankName() : "") + " (" + maskIban(account.getIban()) + ")";
            accountFilter.getItems().add(display);
        }
        accountFilter.getSelectionModel().selectFirst();

        categoryFilter.getItems().clear();
        categoryFilter.getItems().add(I18n.get("filter.allCategories"));
        for (Category category : categoryService.getAllCategories()) {
            categoryFilter.getItems().add(category.getName());
        }
        categoryFilter.getSelectionModel().selectFirst();

    }

    private void setupDateFilters() {
        Locale locale = I18n.getCurrentLocale();
        datePresetFilter.getItems().setAll(TransactionDateFilters.Preset.values());
        datePresetFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(TransactionDateFilters.Preset preset) {
                return preset == null ? "" : I18n.get("filter.datePreset." + preset.name());
            }

            @Override
            public TransactionDateFilters.Preset fromString(String text) {
                return Arrays.stream(TransactionDateFilters.Preset.values())
                        .filter(preset -> toString(preset).equals(text))
                        .findFirst()
                        .orElse(null);
            }
        });

        DateTimeFormatter formatter = TransactionDateFilters.formatter(locale);
        StringConverter<LocalDate> dateConverter = new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : formatter.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                return TransactionDateFilters.parseDate(text, locale);
            }
        };
        fromDateFilter.setConverter(dateConverter);
        toDateFilter.setConverter(dateConverter);
        datePresetFilter.setValue(TransactionDateFilters.Preset.ALL_TIME);

        datePresetFilter.valueProperty().addListener((observable, oldPreset, preset) -> {
            if (preset == null || preset == TransactionDateFilters.Preset.CUSTOM) return;
            TransactionDateFilters.Range range = TransactionDateFilters.rangeFor(
                    preset, LocalDate.now(), locale);
            updatingDateFilters = true;
            fromDateFilter.setValue(range.fromDate());
            toDateFilter.setValue(range.toDate());
            updatingDateFilters = false;
            setDateFieldInvalid(fromDateFilter, false);
            setDateFieldInvalid(toDateFilter, false);
        });

        fromDateFilter.getEditor().textProperty().addListener((observable, oldText, newText) ->
                onDateTextChanged());
        toDateFilter.getEditor().textProperty().addListener((observable, oldText, newText) ->
                onDateTextChanged());
    }

    private void onDateTextChanged() {
        setDateFieldInvalid(fromDateFilter, false);
        setDateFieldInvalid(toDateFilter, false);
        if (!updatingDateFilters) {
            datePresetFilter.setValue(TransactionDateFilters.Preset.CUSTOM);
        }
    }

    private void setDateFieldInvalid(DatePicker datePicker, boolean invalid) {
        if (invalid) {
            if (!datePicker.getEditor().getStyleClass().contains("invalid-date")) {
                datePicker.getEditor().getStyleClass().add("invalid-date");
            }
        } else {
            datePicker.getEditor().getStyleClass().remove("invalid-date");
        }
    }

    private void loadTransactions() {
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = TransactionDateFilters.parseDate(
                    fromDateFilter.getEditor().getText(), I18n.getCurrentLocale());
        } catch (DateTimeParseException e) {
            setDateFieldInvalid(fromDateFilter, true);
            return;
        }
        try {
            toDate = TransactionDateFilters.parseDate(
                    toDateFilter.getEditor().getText(), I18n.getCurrentLocale());
        } catch (DateTimeParseException e) {
            setDateFieldInvalid(toDateFilter, true);
            return;
        }

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            setDateFieldInvalid(fromDateFilter, true);
            setDateFieldInvalid(toDateFilter, true);
            return;
        }
        setDateFieldInvalid(fromDateFilter, false);
        setDateFieldInvalid(toDateFilter, false);

        Long accountId = getSelectedAccountId();
        Long categoryId = getSelectedCategoryId();
        BigDecimal minAmount = parseAmount(minAmountField.getText());
        BigDecimal maxAmount = parseAmount(maxAmountField.getText());
        String search = searchField.getText().trim();
        Set<Long> labelIds = null;

        List<Transaction> filtered = transactionService.filterTransactions(
                accountId, fromDate, toDate, categoryId, labelIds, minAmount, maxAmount,
                search.isEmpty() ? null : search);

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

    private void updateDetailEditingState() {
        boolean singleTransactionSelected = getSelectedTransactions().size() == 1;
        detailForm.setDisable(!singleTransactionSelected);
        detailSaveButton.setDisable(!singleTransactionSelected);
    }

    private Long getSelectedAccountId() {
        int idx = accountFilter.getSelectionModel().getSelectedIndex();
        if (idx <= 0) return null;
        List<BankAccount> accounts = accountService.getAllAccounts();
        return idx - 1 < accounts.size() ? accounts.get(idx - 1).getId() : null;
    }

    private Long getSelectedCategoryId() {
        int idx = categoryFilter.getSelectionModel().getSelectedIndex();
        if (idx <= 0) return null;
        List<Category> categories = categoryService.getAllCategories();
        return idx - 1 < categories.size() ? categories.get(idx - 1).getId() : null;
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return new BigDecimal(text.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String maskIban(String iban) {
        if (iban == null || iban.length() < 8) return iban != null ? iban : "";
        return iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
    }

    @FXML
    private void onApplyFilters() {
        loadTransactions();
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
        payeeField.setText(transaction.getPayeeDisplay() != null ? transaction.getPayeeDisplay() : "");
        detailCategoryId = transaction.getCategoryId();
        detailLabelIds = transaction.getLabelIds() == null
                ? new HashSet<>()
                : new HashSet<>(transaction.getLabelIds());
        updateDetailAssignmentButtons();
        noteArea.setText(transaction.getNote() != null ? transaction.getNote() : "");
    }

    private void updateDetailAssignmentButtons() {
        String categoryName = detailCategoryId == null ? I18n.get("picker.category.clear")
                : categoryService.getCategory(detailCategoryId).map(Category::getName)
                .orElse(I18n.get("picker.category.clear"));
        detailCategoryButton.setText(categoryName);

        String labelNames = detailLabelIds.stream()
                .map(labelService::getLabel)
                .flatMap(Optional::stream)
                .map(org.fintrax.model.Label::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        detailLabelsButton.setText(labelNames.isEmpty() ? I18n.get("picker.labels.choose") : labelNames);
    }

    private void chooseDetailCategory() {
        Category selectedCategory = detailCategoryId == null
                ? null
                : categoryService.getCategory(detailCategoryId).orElse(null);
        SearchableListPicker.chooseOne(rootPane.getScene().getWindow(), I18n.get("picker.category.title"),
                categoryService.getAllCategories(),
                selectedCategory, Category::getName,
                "picker.category.create", name -> categoryService.createCategory(name, null, null))
                .ifPresent(choice -> {
                    detailCategoryId = choice.isEmpty() ? null : choice.get(0).getId();
                    updateDetailAssignmentButtons();
                });
    }

    private void chooseDetailLabels() {
        List<org.fintrax.model.Label> selectedLabels = labelService.getAllLabels().stream()
                .filter(label -> detailLabelIds.contains(label.getId()))
                .toList();
        SearchableListPicker.chooseMany(rootPane.getScene().getWindow(), I18n.get("picker.labels.title"),
                labelService.getAllLabels(), selectedLabels, org.fintrax.model.Label::getName,
                "picker.labels.create", name -> labelService.createLabel(name, "#1976d2", null))
                .ifPresent(labels -> {
                    detailLabelIds = labels.stream().map(org.fintrax.model.Label::getId)
                            .collect(Collectors.toCollection(HashSet::new));
                    updateDetailAssignmentButtons();
                });
    }

    @FXML
    private void onSaveDetail() {
        if (getSelectedTransactions().size() != 1) return;
        Transaction selected = transactionTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String payee = payeeField.getText().trim();
        if (!payee.isEmpty()) {
            transactionService.updatePayeeDisplay(selected.getId(), payee);
        }

        transactionService.updateCategory(selected.getId(), detailCategoryId);
        transactionService.updateLabels(selected.getId(), detailLabelIds);

        String note = noteArea.getText().trim();
        transactionService.updateNote(selected.getId(), note.isEmpty() ? null : note);

        loadTransactions();
        log.info("Saved detail for transaction {}", selected.getId());
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

    private void restoreColumnConfig() {
        AppSetting setting = storeManager.getRoot().getSettings().get(COLUMN_CONFIG_KEY);
        if (setting == null || setting.getValue() == null) return;

        try {
            String[] parts = setting.getValue().split(",");
            for (String part : parts) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    String colName = kv[0];
                    boolean visible = Boolean.parseBoolean(kv[1]);
                    TableColumn<?, ?> col = findColumn(colName);
                    if (col != null) {
                        col.setVisible(visible);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to restore column config", e);
        }
    }

    private TableColumn<?, ?> findColumn(String name) {
        return switch (name) {
            case "date" -> dateColumn;
            case "payee" -> payeeColumn;
            case "purpose" -> purposeColumn;
            case "amount" -> amountColumn;
            case "balance" -> balanceColumn;
            case "category" -> categoryColumn;
            case "labels" -> labelsColumn;
            default -> null;
        };
    }

    public void saveColumnConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("date=").append(dateColumn.isVisible());
        sb.append(",payee=").append(payeeColumn.isVisible());
        sb.append(",purpose=").append(purposeColumn.isVisible());
        sb.append(",amount=").append(amountColumn.isVisible());
        sb.append(",balance=").append(balanceColumn.isVisible());
        sb.append(",category=").append(categoryColumn.isVisible());
        sb.append(",labels=").append(labelsColumn.isVisible());

        AppSetting setting = storeManager.getRoot().getSettings().computeIfAbsent(
                COLUMN_CONFIG_KEY, k -> AppSetting.builder().key(k).build());
        setting.setValue(sb.toString());
        storeManager.store(setting);
    }
}
