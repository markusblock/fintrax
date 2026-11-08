package org.fintrax.ui.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.TransactionFilter;
import org.fintrax.service.TransactionService;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.control.LabelChipFactory;
import org.fintrax.ui.control.TableCellTooltips;
import org.fintrax.ui.control.TransactionTableSetup;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
public class ClassificationsController {
    private static final String COLUMN_CONFIG_KEY = "classification.transaction.columns";
    private static final String AMOUNT_POSITIVE_STYLE = "amount-positive";
    private static final String AMOUNT_NEGATIVE_STYLE = "amount-negative";

    @FXML
    private BorderPane rootPane;
    @FXML
    private TreeView<String> classificationTree;
    @FXML
    private TableView<Transaction> classificationTransactionsTable;
    @FXML
    private Label classificationTransactionsTotalAmount;
    @FXML
    private TransactionFilterPanelController transactionFilterPanelController;

    private final CategoryService categoryService;
    private final LabelService labelService;
    private final SettingsService settingsService;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final TransactionTableActions transactionTableActions;
    private final SessionViewState sessionViewState;
    private final Map<Long, TreeItem<String>> categoryItemsById = new HashMap<>();
    private final Map<Long, TreeItem<String>> labelItemsById = new HashMap<>();
    private final ObservableList<Transaction> displayedTransactions = FXCollections.observableArrayList();
    private TransactionTableSetup transactionTableSetup;
    private final ContextMenu classificationContextMenu = new ContextMenu();
    private final MenuItem addCategoryMenuItem = new MenuItem();
    private final MenuItem addLabelMenuItem = new MenuItem();
    private final MenuItem editNodeMenuItem = new MenuItem();
    private final MenuItem deleteNodeMenuItem = new MenuItem();
    private TreeItem<String> categoriesRoot;
    private TreeItem<String> labelsRoot;

    public ClassificationsController(CategoryService categoryService,
                                  LabelService labelService,
                                  SettingsService settingsService,
                                  TransactionService transactionService,
                                  AccountService accountService,
                                  TransactionTableActions transactionTableActions,
                                  SessionViewState sessionViewState) {
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.settingsService = settingsService;
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.transactionTableActions = transactionTableActions;
        this.sessionViewState = sessionViewState;
    }

    @FXML
    public void initialize() {
        log.info("ClassificationsController initialized");
        loadTree();
        configureContextMenu();
        classificationTree.setCellFactory(this::createClassificationCell);
        setupTransactionTable();
        TableCellTooltips.install(classificationTransactionsTable);
        classificationTransactionsTable.setItems(displayedTransactions);
        setupColumnMenu();
        displayedTransactions.addListener((javafx.collections.ListChangeListener<Transaction>) change ->
                updateTransactionTotal());
        updateTransactionTotal();
        transactionTableActions.installContextMenu(
                classificationTransactionsTable, rootPane, this::reloadSelectedTransactionsKeepingSelection, null);
        transactionFilterPanelController.setCollapsible(true);
        transactionFilterPanelController.restoreForView(SessionViewState.CLASSIFICATIONS_VIEW);
        transactionFilterPanelController.setOnFiltersChanged(this::reloadSelectedTransactionsKeepingSelection);
        classificationTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            saveClassificationSelection(newItem);
            loadSelectedNodeTransactions(true);
        });
        restoreClassificationSelection();
    }

    private void setupColumnMenu() {
        transactionTableSetup.installColumnMenu(COLUMN_CONFIG_KEY, settingsService);
    }

    private void setupTransactionTable() {
        transactionTableSetup = new TransactionTableSetup(classificationTransactionsTable,
                accountService, categoryService, labelService);
        transactionTableSetup.configure();
        transactionTableSetup.categoryColumn().setCellFactory(column -> createAssignmentCell(() ->
                transactionTableActions.setCategory(classificationTransactionsTable, rootPane,
                        this::reloadSelectedTransactionsKeepingSelection)));
        transactionTableSetup.labelsColumn().setCellFactory(column -> createLabelChipCell(() ->
                transactionTableActions.setLabels(classificationTransactionsTable, rootPane,
                        this::reloadSelectedTransactionsKeepingSelection)));
    }

    private void updateTransactionTotal() {
        BigDecimal total = displayedTransactions.stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        classificationTransactionsTotalAmount.setText(formatAmount(total));
        classificationTransactionsTotalAmount.getStyleClass()
                .removeAll(AMOUNT_POSITIVE_STYLE, AMOUNT_NEGATIVE_STYLE);
        classificationTransactionsTotalAmount.getStyleClass()
                .add(total.signum() < 0 ? AMOUNT_NEGATIVE_STYLE : AMOUNT_POSITIVE_STYLE);
    }

    private String formatAmount(BigDecimal amount) {
        return String.format("%,.2f EUR", amount);
    }

    private TableCell<Transaction, String> createAssignmentCell(Runnable openSelection) {
        TableCell<Transaction, String> cell = new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        installAssignmentHandler(cell, openSelection);
        return cell;
    }

    private TableCell<Transaction, String> createLabelChipCell(Runnable openSelection) {
        TableCell<Transaction, String> cell = new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (getGraphic() instanceof HBox previous) previous.prefWidthProperty().unbind();
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }

                Set<Long> labelIds = getTableRow().getItem().getLabelIds();
                List<org.fintrax.model.Label> labels = List.of();
                if (labelIds != null) {
                    labels = labelIds.stream()
                            .map(labelService::getLabel)
                            .flatMap(Optional::stream)
                            .toList();
                }
                HBox chips = LabelChipFactory.createAll(labels);
                chips.prefWidthProperty().bind(Bindings.max(0,
                        transactionTableSetup.labelsColumn().widthProperty().subtract(12)));
                setGraphic(chips.getChildren().isEmpty() ? null : chips);
            }
        };
        installAssignmentHandler(cell, openSelection);
        return cell;
    }

    private void installAssignmentHandler(TableCell<Transaction, String> cell, Runnable openSelection) {
        cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2 || cell.isEmpty()) {
                return;
            }
            if (!classificationTransactionsTable.getSelectionModel().isSelected(cell.getIndex())) {
                classificationTransactionsTable.getSelectionModel().clearAndSelect(cell.getIndex());
            }
            openSelection.run();
        });
    }

    private void loadCategoryTransactions(Long categoryId, boolean clearWhenCriteriaInvalid) {
        loadFilteredTransactions(categoryService.getTransactionsInCategoryAndDescendants(categoryId),
                clearWhenCriteriaInvalid);
    }

    private void loadLabelTransactions(Long labelId, boolean clearWhenCriteriaInvalid) {
        loadFilteredTransactions(labelService.getTransactionsWithLabel(labelId), clearWhenCriteriaInvalid);
    }

    private void loadFilteredTransactions(List<Transaction> source, boolean clearWhenCriteriaInvalid) {
        Optional<TransactionFilter> criteria = transactionFilterPanelController.getCriteria();
        if (criteria.isEmpty()) {
            if (clearWhenCriteriaInvalid) displayedTransactions.clear();
            return;
        }

        displayedTransactions.setAll(transactionService.filterTransactions(source, criteria.get()));
        sortTransactionsIfNeeded();
    }

    private void loadSelectedNodeTransactions(boolean clearWhenCriteriaInvalid) {
        TreeItem<String> selectedItem = classificationTree.getSelectionModel().getSelectedItem();
        Long categoryId = selectedItem == null ? null : findCategoryIdByItem(selectedItem);
        Long labelId = selectedItem == null ? null : findLabelIdByItem(selectedItem);
        if (categoryId != null) {
            loadCategoryTransactions(categoryId, clearWhenCriteriaInvalid);
        } else if (labelId != null) {
            loadLabelTransactions(labelId, clearWhenCriteriaInvalid);
        } else {
            displayedTransactions.clear();
        }
    }

    private void saveClassificationSelection(TreeItem<String> item) {
        Long categoryId = item == null ? null : findCategoryIdByItem(item);
        Long labelId = item == null ? null : findLabelIdByItem(item);
        sessionViewState.setClassificationSelection(categoryId == null && labelId == null
                ? null
                : new SessionViewState.ClassificationSelection(categoryId, labelId));
    }

    private void restoreClassificationSelection() {
        SessionViewState.ClassificationSelection selection = sessionViewState.classificationSelection();
        if (selection == null) return;

        TreeItem<String> item = selection.categoryId() != null
                ? categoryItemsById.get(selection.categoryId())
                : labelItemsById.get(selection.labelId());
        if (item == null) {
            sessionViewState.setClassificationSelection(null);
            return;
        }
        if (selection.categoryId() != null) expandPathToCategory(selection.categoryId());
        classificationTree.getSelectionModel().select(item);
    }

    private void sortTransactionsIfNeeded() {
        if (!classificationTransactionsTable.getSortOrder().isEmpty()) {
            classificationTransactionsTable.sort();
        }
    }

    private void reloadSelectedTransactionsKeepingSelection() {
        Set<Long> selectedIds = classificationTransactionsTable.getSelectionModel().getSelectedItems().stream()
                .map(Transaction::getId)
                .collect(Collectors.toSet());
        loadSelectedNodeTransactions(false);

        for (int index = 0; index < displayedTransactions.size(); index++) {
            if (selectedIds.contains(displayedTransactions.get(index).getId())) {
                classificationTransactionsTable.getSelectionModel().select(index);
            }
        }
    }

    private void configureContextMenu() {
        addCategoryMenuItem.setText(I18n.get("button.addCategory"));
        addCategoryMenuItem.setGraphic(icon("fth-plus", 14));
        addCategoryMenuItem.setId("addCategoryMenuItem");
        addCategoryMenuItem.setOnAction(event -> onAddCategory());

        addLabelMenuItem.setText(I18n.get("button.addLabel"));
        addLabelMenuItem.setGraphic(icon("fth-plus", 14));
        addLabelMenuItem.setId("addLabelMenuItem");
        addLabelMenuItem.setOnAction(event -> showLabelEditor(null));

        editNodeMenuItem.setText(I18n.get("categories.editNode"));
        editNodeMenuItem.setGraphic(icon("fth-edit-2", 14));
        editNodeMenuItem.setId("editNodeMenuItem");
        editNodeMenuItem.setOnAction(event -> openSelectedNodeEditor());

        deleteNodeMenuItem.setText(I18n.get("categories.deleteNode"));
        deleteNodeMenuItem.setGraphic(icon("fth-trash-2", 14));
        deleteNodeMenuItem.setId("deleteNodeMenuItem");
        deleteNodeMenuItem.setOnAction(event -> deleteSelectedNode());

        classificationContextMenu.getItems().setAll(
                addCategoryMenuItem, addLabelMenuItem, editNodeMenuItem, deleteNodeMenuItem);
    }

    private TreeCell<String> createClassificationCell(TreeView<String> treeView) {
        TreeCell<String> cell = new TreeCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(null);
                setText(empty ? null : value);
                if (!empty) {
                    Long labelId = findLabelIdByItem(getTreeItem());
                    if (labelId != null) {
                        labelService.getLabel(labelId).ifPresent(label -> {
                            setText(null);
                            setGraphic(LabelChipFactory.create(label));
                        });
                    }
                }
            }
        };
        cell.setContextMenu(classificationContextMenu);

        cell.setOnMouseClicked(event -> {
            if (cell.isEmpty()) return;

            TreeItem<String> item = cell.getTreeItem();
            classificationTree.getSelectionModel().select(item);
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Long categoryId = findCategoryIdByItem(item);
                if (categoryId != null) {
                    showCategoryEditor(categoryId, null);
                } else if (findLabelIdByItem(item) != null) {
                    labelService.getLabel(findLabelIdByItem(item)).ifPresent(this::showLabelEditor);
                }
            }
        });

        cell.setOnContextMenuRequested(event -> {
            if (cell.isEmpty()) return;

            TreeItem<String> item = cell.getTreeItem();
            classificationTree.getSelectionModel().select(item);
            Long categoryId = findCategoryIdByItem(item);
            Long labelId = findLabelIdByItem(item);
            boolean isCategoriesRoot = item == categoriesRoot;
            addCategoryMenuItem.setDisable(!(isCategoriesRoot
                    || categoryId != null && calculateDepth(categoryId) < 5));
            addLabelMenuItem.setDisable(false);
            editNodeMenuItem.setDisable(categoryId == null && labelId == null);
            deleteNodeMenuItem.setDisable(categoryId == null && labelId == null);
            classificationContextMenu.show(cell, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return cell;
    }

    private void loadTree() {
        categoryItemsById.clear();
        labelItemsById.clear();
        displayedTransactions.clear();

        TreeItem<String> root = new TreeItem<>("");
        root.setExpanded(true);
        categoriesRoot = new TreeItem<>(I18n.get("classifications.categoriesRoot"));
        categoriesRoot.setExpanded(true);
        labelsRoot = new TreeItem<>(I18n.get("classifications.labelsRoot"));
        labelsRoot.setExpanded(true);
        root.getChildren().addAll(categoriesRoot, labelsRoot);

        List<Category> allCategories = categoryService.getAllCategories();
        Map<Long, List<Category>> childrenMap = allCategories.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : -1L));

        buildTree(categoriesRoot, childrenMap.getOrDefault(-1L, List.of()), childrenMap);
        for (org.fintrax.model.Label label : labelService.getAllLabels()) {
            TreeItem<String> item = new TreeItem<>(label.getName());
            labelsRoot.getChildren().add(item);
            labelItemsById.put(label.getId(), item);
        }

        classificationTree.setShowRoot(false);
        classificationTree.setRoot(root);
    }

    private void buildTree(TreeItem<String> parent, List<Category> children, Map<Long, List<Category>> childrenMap) {
        for (Category cat : children) {
            TreeItem<String> item = new TreeItem<>(cat.getName());
            parent.getChildren().add(item);
            categoryItemsById.put(cat.getId(), item);

            List<Category> subChildren = childrenMap.getOrDefault(cat.getId(), List.of());
            if (!subChildren.isEmpty()) {
                buildTree(item, subChildren, childrenMap);
            }
        }
    }

    private Long findCategoryIdByItem(TreeItem<String> item) {
        for (Map.Entry<Long, TreeItem<String>> entry : categoryItemsById.entrySet()) {
            if (entry.getValue() == item) return entry.getKey();
        }
        return null;
    }

    private Long findLabelIdByItem(TreeItem<String> item) {
        for (Map.Entry<Long, TreeItem<String>> entry : labelItemsById.entrySet()) {
            if (entry.getValue() == item) return entry.getKey();
        }
        return null;
    }

    private int calculateDepth(Long categoryId) {
        int depth = 0;
        Long currentId = categoryId;
        while (currentId != null) {
            Category parent = categoryService.getCategory(currentId).orElse(null);
            if (parent == null) break;
            depth++;
            currentId = parent.getParentId();
        }
        return depth;
    }

    private void onAddCategory() {
        TreeItem<String> selectedItem = classificationTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;

        Long parentId = findCategoryIdByItem(selectedItem);
        if (parentId != null && calculateDepth(parentId) >= 5) {
            showError(I18n.get("categories.maxDepth"));
            return;
        }

        showCategoryEditor(null, parentId);
    }

    private void openSelectedNodeEditor() {
        TreeItem<String> selectedItem = classificationTree.getSelectionModel().getSelectedItem();
        Long categoryId = selectedItem == null ? null : findCategoryIdByItem(selectedItem);
        if (categoryId != null) {
            showCategoryEditor(categoryId, null);
            return;
        }
        Long labelId = selectedItem == null ? null : findLabelIdByItem(selectedItem);
        if (labelId != null) {
            labelService.getLabel(labelId).ifPresent(this::showLabelEditor);
        }
    }

    private void deleteSelectedNode() {
        TreeItem<String> selectedItem = classificationTree.getSelectionModel().getSelectedItem();
        Long categoryId = selectedItem == null ? null : findCategoryIdByItem(selectedItem);
        if (categoryId != null) {
            confirmDeleteCategory(categoryId);
            return;
        }
        Long labelId = selectedItem == null ? null : findLabelIdByItem(selectedItem);
        if (labelId != null) {
            confirmDeleteLabel(labelId);
        }
    }

    private void showCategoryEditor(Long categoryId, Long parentId) {
        Category category = categoryId == null
                ? null
                : categoryService.getCategory(categoryId).orElse(null);
        if (categoryId != null && category == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.setTitle(I18n.get("categories.editor.title"));
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);

        TextField nameField = new TextField(category == null ? "" : nullToEmpty(category.getName()));
        nameField.setId("nameField");
        TextField commentField = new TextField(category == null ? "" : nullToEmpty(category.getComment()));
        commentField.setId("commentField");
        ComboBox<CategoryParentChoice> parentSelector = createParentSelectorForEditing(category);
        Label errorLabel = new Label();
        errorLabel.setId("errorLabel");

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.getColumnConstraints().addAll(new ColumnConstraints(90), new ColumnConstraints());
        fields.add(new Label(I18n.get("label.name")), 0, 0);
        fields.add(nameField, 1, 0);
        int commentRow = addParentField(fields, parentSelector);
        fields.add(new Label(I18n.get("label.comment")), 0, commentRow);
        fields.add(commentField, 1, commentRow);

        VBox content = new VBox(10, fields, errorLabel);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(5, 0, 0, 0));
        content.setMinWidth(360);

        ButtonType saveType = new ButtonType(I18n.get("button.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(I18n.get("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setContent(content);
        dialogPane.getButtonTypes().setAll(saveType, cancelType);
        Button saveButton = (Button) dialogPane.lookupButton(saveType);
        saveButton.setId("saveButton");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (nameField.getText().isBlank()) {
                errorLabel.setText(I18n.get("categories.name.required"));
                event.consume();
            }
        });
        ((Button) dialogPane.lookupButton(cancelType)).setId("cancelButton");

        dialog.setResultConverter(buttonType -> buttonType);
        dialog.setOnHidden(event -> {
            ButtonType result = dialog.getResult();
            if (result == saveType) {
                Long selectedParentId = getSelectedParentId(parentId, parentSelector);
                saveCategory(category, parentId, selectedParentId,
                        nameField.getText().trim(), commentField.getText().trim());
            }
        });
        dialog.setOnShown(event -> nameField.requestFocus());
        dialog.show();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private ComboBox<CategoryParentChoice> createParentSelectorForEditing(Category category) {
        return category == null ? null : createParentSelector(category);
    }

    private int addParentField(GridPane fields, ComboBox<CategoryParentChoice> parentSelector) {
        if (parentSelector == null) return 1;
        fields.add(new Label(I18n.get("categories.parent")), 0, 1);
        fields.add(parentSelector, 1, 1);
        return 2;
    }

    private Long getSelectedParentId(Long parentId, ComboBox<CategoryParentChoice> parentSelector) {
        if (parentSelector == null) return parentId;
        CategoryParentChoice choice = parentSelector.getValue();
        return choice == null ? null : choice.id();
    }

    private ComboBox<CategoryParentChoice> createParentSelector(Category category) {
        ComboBox<CategoryParentChoice> selector = new ComboBox<>();
        selector.setId("parentCategoryCombo");
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setConverter(new StringConverter<>() {
            @Override
            public String toString(CategoryParentChoice choice) {
                return choice == null ? "" : choice.label();
            }

            @Override
            public CategoryParentChoice fromString(String value) {
                return selector.getItems().stream()
                        .filter(choice -> choice.label().equals(value))
                        .findFirst()
                        .orElse(null);
            }
        });

        List<CategoryParentChoice> choices = new ArrayList<>();
        choices.add(new CategoryParentChoice(null, I18n.get("classifications.categoriesRoot")));
        Set<Long> excludedIds = getCategorySubtreeIds(category.getId());
        int subtreeHeight = calculateSubtreeHeight(category.getId());
        categoryService.getAllCategories().stream()
                .filter(candidate -> !excludedIds.contains(candidate.getId()))
                .filter(candidate -> calculateDepth(candidate.getId()) + subtreeHeight <= 5)
                .map(candidate -> new CategoryParentChoice(candidate.getId(), categoryPath(candidate)))
                .sorted(Comparator.comparing(CategoryParentChoice::label, String.CASE_INSENSITIVE_ORDER))
                .forEach(choices::add);
        selector.getItems().setAll(choices);
        selector.setValue(choices.stream()
                .filter(choice -> Objects.equals(choice.id(), category.getParentId()))
                .findFirst()
                .orElse(choices.getFirst()));
        return selector;
    }

    private Set<Long> getCategorySubtreeIds(Long categoryId) {
        Set<Long> ids = new HashSet<>();
        ids.add(categoryId);
        for (Category child : categoryService.getChildCategories(categoryId)) {
            ids.addAll(getCategorySubtreeIds(child.getId()));
        }
        return ids;
    }

    private int calculateSubtreeHeight(Long categoryId) {
        return 1 + categoryService.getChildCategories(categoryId).stream()
                .mapToInt(child -> calculateSubtreeHeight(child.getId()))
                .max()
                .orElse(0);
    }

    private String categoryPath(Category category) {
        Deque<String> names = new ArrayDeque<>();
        Category current = category;
        while (current != null) {
            names.addFirst(current.getName());
            current = current.getParentId() == null
                    ? null
                    : categoryService.getCategory(current.getParentId()).orElse(null);
        }
        return String.join(" / ", names);
    }

    private void saveCategory(Category category, Long parentId, Long selectedParentId, String name, String comment) {
        try {
            if (category == null) {
                categoryService.createCategory(name, parentId, emptyToNull(comment));
            } else {
                categoryService.updateCategory(category.getId(), name, selectedParentId, emptyToNull(comment));
            }
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            return;
        }

        loadTree();
        Long categoryIdToSelect = category == null ? null : category.getId();
        Long parentIdToExpand = category == null ? parentId : selectedParentId;
        if (parentIdToExpand != null) expandPathToCategory(parentIdToExpand);
        if (categoryIdToSelect != null) {
            expandPathToCategory(categoryIdToSelect);
            classificationTree.getSelectionModel().select(categoryItemsById.get(categoryIdToSelect));
        }
        log.info("Saved category");
    }

    private String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private void expandPathToCategory(Long categoryId) {
        TreeItem<String> item = categoryItemsById.get(categoryId);
        while (item != null) {
            item.setExpanded(true);
            item = item.getParent();
        }
    }

    private void confirmDeleteCategory(Long categoryId) {
        Category category = categoryService.getCategory(categoryId).orElse(null);
        if (category == null) return;

        List<Category> children = categoryService.getChildCategories(categoryId);
        String message = children.isEmpty()
                ? I18n.get("categories.delete.confirm", category.getName())
                : I18n.get("categories.delete.withChildren", category.getName());
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, ButtonType.OK, ButtonType.CANCEL);
        FintraxUI.configureDialog(alert);
        alert.setTitle(I18n.get("categories.delete.title"));
        alert.initOwner(rootPane.getScene().getWindow());

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        VBox content = new VBox(10, messageLabel);
        content.getStyleClass().add("dialog-content");
        CheckBox moveChildrenCheckBox = null;
        if (!children.isEmpty()) {
            String targetName = category.getParentId() == null
                    ? I18n.get("categories.delete.topLevel")
                    : categoryService.getCategory(category.getParentId())
                            .map(Category::getName)
                            .orElse(I18n.get("categories.delete.topLevel"));
            moveChildrenCheckBox = new CheckBox(I18n.get("categories.delete.moveChildrenToParent", targetName));
            moveChildrenCheckBox.setId("moveChildrenCheckBox");
            content.getChildren().add(moveChildrenCheckBox);
        }
        alert.getDialogPane().setContent(content);

        Button confirmButton = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        confirmButton.setId("confirmDeleteButton");
        CheckBox finalMoveChildrenCheckBox = moveChildrenCheckBox;
        alert.setOnHidden(event -> {
            if (alert.getResult() == ButtonType.OK) {
                categoryService.deleteCategory(categoryId, null,
                        finalMoveChildrenCheckBox == null || finalMoveChildrenCheckBox.isSelected());
                loadTree();
            }
        });
        alert.show();
    }

    private void showLabelEditor(org.fintrax.model.Label label) {
        Dialog<ButtonType> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.setTitle(I18n.get("labels.editor.title"));
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);

        TextField nameField = new TextField(label == null ? "" : nullToEmpty(label.getName()));
        nameField.setId("labelNameField");
        ColorPicker colorPicker = new ColorPicker();
        colorPicker.setId("labelColorPicker");
        colorPicker.setValue(label == null ? Color.web("#1976d2") : parseLabelColor(label.getColor()));
        TextField commentField = new TextField(label == null ? "" : nullToEmpty(label.getComment()));
        commentField.setId("labelCommentField");
        Label errorLabel = new Label();
        errorLabel.setId("labelErrorLabel");

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.getColumnConstraints().addAll(new ColumnConstraints(90), new ColumnConstraints());
        fields.add(new Label(I18n.get("label.name")), 0, 0);
        fields.add(nameField, 1, 0);
        fields.add(new Label(I18n.get("label.color")), 0, 1);
        fields.add(colorPicker, 1, 1);
        fields.add(new Label(I18n.get("label.comment")), 0, 2);
        fields.add(commentField, 1, 2);

        VBox content = new VBox(10, fields, errorLabel);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(5, 0, 0, 0));
        content.setMinWidth(360);

        ButtonType saveType = new ButtonType(I18n.get("button.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(I18n.get("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setContent(content);
        dialogPane.getButtonTypes().setAll(saveType, cancelType);
        Button saveButton = (Button) dialogPane.lookupButton(saveType);
        saveButton.setId("labelSaveButton");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (nameField.getText().isBlank()) {
                errorLabel.setText(I18n.get("labels.name.required"));
                event.consume();
            }
        });
        ((Button) dialogPane.lookupButton(cancelType)).setId("labelCancelButton");

        dialog.setResultConverter(buttonType -> buttonType);
        dialog.setOnHidden(event -> {
            if (dialog.getResult() != saveType) return;
            String name = nameField.getText().trim();
            String color = colorToHex(colorPicker.getValue());
            String comment = commentField.getText().trim();
            try {
                if (label == null) {
                    labelService.createLabel(name, color, emptyToNull(comment));
                } else {
                    labelService.updateLabel(label.getId(), name, color, emptyToNull(comment));
                }
                loadTree();
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
        dialog.setOnShown(event -> nameField.requestFocus());
        dialog.show();
    }

    private Color parseLabelColor(String color) {
        if (color == null || color.isBlank()) return Color.web("#1976d2");
        try {
            return Color.web(color);
        } catch (IllegalArgumentException exception) {
            return Color.BLACK;
        }
    }

    private String colorToHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private void confirmDeleteLabel(Long labelId) {
        org.fintrax.model.Label label = labelService.getLabel(labelId).orElse(null);
        if (label == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.get("labels.delete.confirm"), ButtonType.OK, ButtonType.CANCEL);
        FintraxUI.configureDialog(alert);
        alert.setTitle(I18n.get("labels.delete.title"));
        alert.initOwner(rootPane.getScene().getWindow());
        alert.setOnHidden(event -> {
            if (alert.getResult() == ButtonType.OK) {
                labelService.deleteLabel(labelId);
                loadTree();
            }
        });
        alert.show();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        FintraxUI.configureDialog(alert);
        alert.setTitle("Error");
        alert.initOwner(rootPane.getScene().getWindow());
        alert.showAndWait();
    }

    private FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(literal);
        icon.setIconSize(size);
        return icon;
    }

    private record CategoryParentChoice(Long id, String label) {
    }
}
