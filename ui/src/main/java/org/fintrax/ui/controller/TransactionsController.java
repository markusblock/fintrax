package org.fintrax.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.*;
import org.fintrax.store.StoreManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class TransactionsController {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String COLUMN_CONFIG_KEY = "transaction.columns";

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
    private Button bulkCategoryButton;
    @FXML
    private Button bulkLabelAddButton;
    @FXML
    private Button bulkLabelRemoveButton;
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
    private TextField payeeField;
    @FXML
    private ComboBox<String> detailCategoryCombo;
    @FXML
    private HBox labelsContainer;
    @FXML
    private TextArea noteArea;

    private final AccountService accountService = ServiceRegistry.getInstance().getAccountService();
    private final TransactionService transactionService = ServiceRegistry.getInstance().getTransactionService();
    private final CategoryService categoryService = ServiceRegistry.getInstance().getCategoryService();
    private final LabelService labelService = ServiceRegistry.getInstance().getLabelService();
    private final StoreManager storeManager = ServiceRegistry.getInstance().getStoreManager();

    private final ObservableList<Transaction> tableData = FXCollections.observableArrayList();
    private boolean detailVisible = false;
    private Map<Long, CheckBox> labelCheckBoxes = new HashMap<>();

    @FXML
    public void initialize() {
        log.info("TransactionsController initialized");
        setupTableColumns();
        loadFilterOptions();
        loadTransactions();
        restoreColumnConfig();

        transactionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadDetail(newVal);
            }
        });

        transactionTable.getSelectionModel().getSelectedIndices().addListener(
                (javafx.collections.ListChangeListener<Integer>) c -> updateBulkButtons());
    }

    private void setupTableColumns() {
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

        labelsColumn.setCellValueFactory(cd -> {
            Set<Long> labelIds = cd.getValue().getLabelIds();
            if (labelIds == null || labelIds.isEmpty()) return new SimpleStringProperty("");
            String text = labelIds.stream()
                    .map(id -> labelService.getLabel(id).map(org.fintrax.model.Label::getName).orElse(""))
                    .filter(s -> !((String) s).isEmpty())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(text);
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

        detailCategoryCombo.getItems().clear();
        detailCategoryCombo.getItems().add("");
        for (Category category : categoryService.getAllCategories()) {
            detailCategoryCombo.getItems().add(category.getName());
        }
    }

    private void loadTransactions() {
        Long accountId = getSelectedAccountId();
        Long categoryId = getSelectedCategoryId();
        BigDecimal minAmount = parseAmount(minAmountField.getText());
        BigDecimal maxAmount = parseAmount(maxAmountField.getText());
        String search = searchField.getText().trim();
        Set<Long> labelIds = null;

        List<Transaction> filtered = transactionService.filterTransactions(
                accountId, null, null, categoryId, labelIds, minAmount, maxAmount,
                search.isEmpty() ? null : search);

        tableData.setAll(filtered);
        transactionTable.setItems(tableData);
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
    }

    private void loadDetail(Transaction transaction) {
        payeeField.setText(transaction.getPayeeDisplay() != null ? transaction.getPayeeDisplay() : "");

        if (transaction.getCategoryId() != null) {
            categoryService.getCategory(transaction.getCategoryId())
                    .ifPresent(c -> detailCategoryCombo.setValue(c.getName()));
        } else {
            detailCategoryCombo.setValue("");
        }

        noteArea.setText(transaction.getNote() != null ? transaction.getNote() : "");

        loadLabelCheckBoxes(transaction);
    }

    private void loadLabelCheckBoxes(Transaction transaction) {
        labelsContainer.getChildren().clear();
        labelCheckBoxes.clear();

        List<org.fintrax.model.Label> allLabels = labelService.getAllLabels();
        Set<Long> transactionLabelIds = transaction.getLabelIds() != null ? transaction.getLabelIds() : Set.of();

        for (org.fintrax.model.Label label : allLabels) {
            CheckBox cb = new CheckBox(label.getName());
            cb.setSelected(transactionLabelIds.contains(label.getId()));
            cb.setStyle(label.getColor() != null ? "-fx-text-fill: " + label.getColor() + ";" : "");
            labelCheckBoxes.put(label.getId(), cb);
            labelsContainer.getChildren().add(cb);
        }
    }

    @FXML
    private void onSaveDetail() {
        Transaction selected = transactionTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String payee = payeeField.getText().trim();
        if (!payee.isEmpty()) {
            transactionService.updatePayeeDisplay(selected.getId(), payee);
        }

        String catName = detailCategoryCombo.getValue();
        if (catName != null && !catName.isEmpty()) {
            Long catId = categoryService.getAllCategories().stream()
                    .filter(c -> c.getName().equals(catName))
                    .map(Category::getId)
                    .findFirst()
                    .orElse(null);
            transactionService.updateCategory(selected.getId(), catId);
        } else {
            transactionService.updateCategory(selected.getId(), null);
        }

        Set<Long> selectedLabelIds = labelCheckBoxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        transactionService.updateLabels(selected.getId(), selectedLabelIds);

        String note = noteArea.getText().trim();
        transactionService.updateNote(selected.getId(), note.isEmpty() ? null : note);

        loadTransactions();
        log.info("Saved detail for transaction {}", selected.getId());
    }

    @FXML
    private void onBulkCategory() {
        List<Transaction> selected = getSelectedTransactions();
        if (selected.isEmpty()) return;

        List<String> categoryNames = categoryService.getAllCategories().stream()
                .map(Category::getName)
                .toList();

        ChoiceDialog<String> dialog = new ChoiceDialog<>(categoryNames.isEmpty() ? "" : categoryNames.get(0), categoryNames);
        dialog.setTitle(I18n.get("dialog.bulkCategory.title"));
        dialog.setHeaderText(I18n.get("dialog.bulkCategory.header", selected.size()));
        dialog.setContentText(I18n.get("dialog.bulkCategory.prompt"));
        dialog.initOwner(rootPane.getScene().getWindow());

        dialog.showAndWait().ifPresent(name -> {
            Long catId = categoryService.getAllCategories().stream()
                    .filter(c -> c.getName().equals(name))
                    .map(Category::getId)
                    .findFirst()
                    .orElse(null);
            if (catId != null) {
                transactionService.bulkUpdateCategory(
                        selected.stream().map(Transaction::getId).toList(), catId);
                loadTransactions();
            }
        });
    }

    @FXML
    private void onBulkLabelAdd() {
        List<Transaction> selected = getSelectedTransactions();
        if (selected.isEmpty()) return;

        List<String> labelNames = labelService.getAllLabels().stream()
                .map(org.fintrax.model.Label::getName)
                .toList();

        ChoiceDialog<String> dialog = new ChoiceDialog<>(labelNames.isEmpty() ? "" : labelNames.get(0), labelNames);
        dialog.setTitle(I18n.get("dialog.bulkLabel.title"));
        dialog.setHeaderText(I18n.get("dialog.bulkLabelAdd.header", selected.size()));
        dialog.setContentText(I18n.get("dialog.bulkLabel.prompt"));
        dialog.initOwner(rootPane.getScene().getWindow());

        dialog.showAndWait().ifPresent(name -> {
            Long labelId = labelService.getAllLabels().stream()
                    .filter(l -> l.getName().equals(name))
                    .map(org.fintrax.model.Label::getId)
                    .findFirst()
                    .orElse(null);
            if (labelId != null) {
                transactionService.bulkAddLabel(
                        selected.stream().map(Transaction::getId).toList(), labelId);
                loadTransactions();
            }
        });
    }

    @FXML
    private void onBulkLabelRemove() {
        List<Transaction> selected = getSelectedTransactions();
        if (selected.isEmpty()) return;

        List<String> labelNames = labelService.getAllLabels().stream()
                .map(org.fintrax.model.Label::getName)
                .toList();

        ChoiceDialog<String> dialog = new ChoiceDialog<>(labelNames.isEmpty() ? "" : labelNames.get(0), labelNames);
        dialog.setTitle(I18n.get("dialog.bulkLabel.title"));
        dialog.setHeaderText(I18n.get("dialog.bulkLabelRemove.header", selected.size()));
        dialog.setContentText(I18n.get("dialog.bulkLabel.prompt"));
        dialog.initOwner(rootPane.getScene().getWindow());

        dialog.showAndWait().ifPresent(name -> {
            Long labelId = labelService.getAllLabels().stream()
                    .filter(l -> l.getName().equals(name))
                    .map(org.fintrax.model.Label::getId)
                    .findFirst()
                    .orElse(null);
            if (labelId != null) {
                transactionService.bulkRemoveLabel(
                        selected.stream().map(Transaction::getId).toList(), labelId);
                loadTransactions();
            }
        });
    }

    private List<Transaction> getSelectedTransactions() {
        return new ArrayList<>(transactionTable.getSelectionModel().getSelectedItems());
    }

    private void updateBulkButtons() {
        int count = transactionTable.getSelectionModel().getSelectedItems().size();
        bulkCategoryButton.setDisable(count == 0);
        bulkLabelAddButton.setDisable(count == 0);
        bulkLabelRemoveButton.setDisable(count == 0);
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
