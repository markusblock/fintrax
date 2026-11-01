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
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.*;
import org.fintrax.ui.control.LabelChipFactory;
import org.fintrax.ui.control.SearchableListPicker;
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
    private TextField payeeField;
    @FXML
    private Button detailCategoryButton;
    @FXML
    private Button detailLabelsButton;
    @FXML
    private TextArea noteArea;
    @FXML
    private Button detailSaveButton;

    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final SettingsService settingsService;
    private final TransactionTableActions transactionTableActions;

    private final ObservableList<Transaction> tableData = FXCollections.observableArrayList();
    private boolean detailVisible = false;
    private Long detailCategoryId;
    private Set<Long> detailLabelIds = new HashSet<>();

    public TransactionsController(TransactionService transactionService,
                                  CategoryService categoryService, LabelService labelService,
                                  SettingsService settingsService, TransactionTableActions transactionTableActions) {
        this.transactionService = transactionService;
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

    private void updateDetailEditingState() {
        boolean singleTransactionSelected = getSelectedTransactions().size() == 1;
        detailForm.setDisable(!singleTransactionSelected);
        detailSaveButton.setDisable(!singleTransactionSelected);
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

    private void setupColumnMenu() {
        TableColumnVisibilityMenu.install(
                transactionTable, columnMenuButton, COLUMN_CONFIG_KEY, settingsService, columnConfigurations());
    }

    private List<TableColumnVisibilityMenu.Column> columnConfigurations() {
        return List.of(
                new TableColumnVisibilityMenu.Column("date", dateColumn),
                new TableColumnVisibilityMenu.Column("payee", payeeColumn),
                new TableColumnVisibilityMenu.Column("purpose", purposeColumn),
                new TableColumnVisibilityMenu.Column("amount", amountColumn),
                new TableColumnVisibilityMenu.Column("balance", balanceColumn),
                new TableColumnVisibilityMenu.Column("category", categoryColumn),
                new TableColumnVisibilityMenu.Column("labels", labelsColumn));
    }
}
