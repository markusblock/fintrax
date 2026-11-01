package org.fintrax.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.TransactionFilter;
import org.fintrax.service.TransactionService;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.control.LabelChipFactory;
import org.fintrax.ui.control.SingleLineText;
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
public class ClassificationsController {
    private static final String COLUMN_CONFIG_KEY = "classification.transaction.columns";

    @FXML
    private BorderPane rootPane;
    @FXML
    private TreeView<String> classificationTree;
    @FXML
    private TableView<Transaction> classificationTransactionsTable;
    @FXML
    private MenuButton columnMenuButton;
    @FXML
    private TableColumn<Transaction, LocalDate> transactionDateColumn;
    @FXML
    private TableColumn<Transaction, String> transactionPayeeColumn;
    @FXML
    private TableColumn<Transaction, String> transactionPurposeColumn;
    @FXML
    private TableColumn<Transaction, BigDecimal> transactionAmountColumn;
    @FXML
    private TableColumn<Transaction, String> transactionCategoryColumn;
    @FXML
    private TableColumn<Transaction, String> transactionLabelsColumn;
    @FXML
    private TransactionFilterPanelController transactionFilterPanelController;

    private final CategoryService categoryService;
    private final LabelService labelService;
    private final SettingsService settingsService;
    private final TransactionService transactionService;
    private final TransactionTableActions transactionTableActions;
    private final Map<Long, TreeItem<String>> categoryItemsById = new HashMap<>();
    private final Map<Long, TreeItem<String>> labelItemsById = new HashMap<>();
    private final ObservableList<Transaction> displayedTransactions = FXCollections.observableArrayList();
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
                                  TransactionTableActions transactionTableActions) {
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.settingsService = settingsService;
        this.transactionService = transactionService;
        this.transactionTableActions = transactionTableActions;
    }

    @FXML
    public void initialize() {
        log.info("ClassificationsController initialized");
        loadTree();
        configureContextMenu();
        classificationTree.setCellFactory(this::createClassificationCell);
        setupTransactionTable();
        setupColumnMenu();
        classificationTransactionsTable.setItems(displayedTransactions);
        transactionTableActions.installContextMenu(
                classificationTransactionsTable, rootPane, this::reloadSelectedTransactionsKeepingSelection);
        transactionFilterPanelController.setCollapsible(true);
        transactionFilterPanelController.setOnFiltersChanged(this::reloadSelectedTransactionsKeepingSelection);
        classificationTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            loadSelectedNodeTransactions(true);
        });
    }

    private void setupColumnMenu() {
        TableColumnVisibilityMenu.install(classificationTransactionsTable, columnMenuButton,
                COLUMN_CONFIG_KEY, settingsService, List.of(
                new TableColumnVisibilityMenu.Column("date", transactionDateColumn),
                new TableColumnVisibilityMenu.Column("payee", transactionPayeeColumn),
                new TableColumnVisibilityMenu.Column("purpose", transactionPurposeColumn),
                new TableColumnVisibilityMenu.Column("amount", transactionAmountColumn),
                new TableColumnVisibilityMenu.Column("category", transactionCategoryColumn),
                new TableColumnVisibilityMenu.Column("labels", transactionLabelsColumn)));
    }

    private void setupTransactionTable() {
        classificationTransactionsTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        transactionDateColumn.setCellValueFactory(cell ->
                new SimpleObjectProperty<>(cell.getValue().getBookingDate()));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        transactionDateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(dateFormatter));
            }
        });

        transactionPayeeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                Objects.toString(cell.getValue().getPayeeDisplay() != null
                        ? cell.getValue().getPayeeDisplay() : cell.getValue().getOriginalPayee(), "")));
        transactionPurposeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(SingleLineText.normalize(cell.getValue().getPurpose())));

        transactionAmountColumn.setCellValueFactory(cell ->
                new SimpleObjectProperty<>(cell.getValue().getAmount()));
        transactionAmountColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("amount-positive", "amount-negative");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.2f EUR", item.doubleValue()));
                    getStyleClass().add(item.signum() < 0 ? "amount-negative" : "amount-positive");
                }
            }
        });

        transactionCategoryColumn.setCellValueFactory(cell -> {
            Long categoryId = cell.getValue().getCategoryId();
            return new SimpleStringProperty(categoryId == null ? "" : categoryService.getCategory(categoryId)
                    .map(Category::getName).orElse(""));
        });
        transactionCategoryColumn.setCellFactory(column -> createAssignmentCell(() ->
                transactionTableActions.setCategory(classificationTransactionsTable, rootPane,
                        this::reloadSelectedTransactionsKeepingSelection)));

        transactionLabelsColumn.setCellValueFactory(cell -> {
            Set<Long> labelIds = cell.getValue().getLabelIds();
            if (labelIds == null || labelIds.isEmpty()) return new SimpleStringProperty("");
            String text = labelIds.stream()
                    .map(id -> labelService.getLabel(id).map(org.fintrax.model.Label::getName).orElse(""))
                    .filter(name -> !name.isEmpty())
                    .collect(Collectors.joining(", "));
            return new SimpleStringProperty(text);
        });
        transactionLabelsColumn.setCellFactory(column -> createLabelChipCell(() ->
                transactionTableActions.setLabels(classificationTransactionsTable, rootPane,
                        this::reloadSelectedTransactionsKeepingSelection)));

        transactionDateColumn.setSortable(true);
        transactionPayeeColumn.setSortable(true);
        transactionPurposeColumn.setSortable(true);
        transactionAmountColumn.setSortable(true);
        transactionCategoryColumn.setSortable(true);
        transactionLabelsColumn.setSortable(true);
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
                        transactionLabelsColumn.widthProperty().subtract(12)));
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
        Label errorLabel = new Label();
        errorLabel.setId("errorLabel");

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.getColumnConstraints().addAll(new ColumnConstraints(90), new ColumnConstraints());
        fields.add(new Label(I18n.get("label.name")), 0, 0);
        fields.add(nameField, 1, 0);
        fields.add(new Label(I18n.get("label.comment")), 0, 1);
        fields.add(commentField, 1, 1);

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
                saveCategory(category, parentId, nameField.getText().trim(), commentField.getText().trim());
            }
        });
        dialog.setOnShown(event -> nameField.requestFocus());
        dialog.show();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void saveCategory(Category category, Long parentId, String name, String comment) {
        Long parentIdToExpand = category == null ? parentId : null;
        if (category == null) {
            categoryService.createCategory(name, parentId, emptyToNull(comment));
        } else {
            categoryService.updateCategory(category.getId(), name, emptyToNull(comment));
        }

        loadTree();
        if (parentIdToExpand != null) {
            expandPathToCategory(parentIdToExpand);
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
}
