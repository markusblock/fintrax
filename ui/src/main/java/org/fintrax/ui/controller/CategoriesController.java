package org.fintrax.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.CategoryService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
public class CategoriesController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML
    private BorderPane rootPane;
    @FXML
    private TreeView<String> categoryTree;
    @FXML
    private TableView<Transaction> categoryTransactionsTable;
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

    private final CategoryService categoryService;
    private final Map<Long, TreeItem<String>> itemByCategoryId = new HashMap<>();
    private final ObservableList<Transaction> categoryTransactions = FXCollections.observableArrayList();
    private final ContextMenu categoryContextMenu = new ContextMenu();
    private final MenuItem addCategoryMenuItem = new MenuItem();
    private final MenuItem editNodeMenuItem = new MenuItem();
    private final MenuItem deleteNodeMenuItem = new MenuItem();

    public CategoriesController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @FXML
    public void initialize() {
        log.info("CategoriesController initialized");
        loadTree();
        configureContextMenu();
        categoryTree.setCellFactory(this::createCategoryCell);
        setupTransactionTable();
        categoryTransactionsTable.setItems(categoryTransactions);
        categoryTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            Long categoryId = newItem == null ? null : findCategoryIdByItem(newItem);
            loadCategoryTransactions(categoryId);
        });
    }

    private void setupTransactionTable() {
        transactionDateColumn.setCellValueFactory(cell ->
                new SimpleObjectProperty<>(cell.getValue().getBookingDate()));
        transactionDateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(DATE_FORMAT));
            }
        });

        transactionPayeeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                Objects.toString(cell.getValue().getPayeeDisplay() != null
                        ? cell.getValue().getPayeeDisplay() : cell.getValue().getOriginalPayee(), "")));
        transactionPurposeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(Objects.toString(cell.getValue().getPurpose(), "")));

        transactionAmountColumn.setCellValueFactory(cell ->
                new SimpleObjectProperty<>(cell.getValue().getAmount()));
        transactionAmountColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.2f EUR", item.doubleValue()));
            }
        });

        transactionCategoryColumn.setCellValueFactory(cell -> {
            Long categoryId = cell.getValue().getCategoryId();
            return new SimpleStringProperty(categoryId == null ? "" : categoryService.getCategory(categoryId)
                    .map(Category::getName).orElse(""));
        });

        transactionDateColumn.setSortable(true);
        transactionPayeeColumn.setSortable(true);
        transactionPurposeColumn.setSortable(true);
        transactionAmountColumn.setSortable(true);
        transactionCategoryColumn.setSortable(true);
    }

    private void loadCategoryTransactions(Long categoryId) {
        if (categoryId == null) {
            categoryTransactions.clear();
            return;
        }

        categoryTransactions.setAll(categoryService.getTransactionsInCategoryAndDescendants(categoryId));
        if (!categoryTransactionsTable.getSortOrder().isEmpty()) {
            categoryTransactionsTable.sort();
        }
    }

    private void configureContextMenu() {
        addCategoryMenuItem.setText(I18n.get("button.addCategory"));
        addCategoryMenuItem.setId("addCategoryMenuItem");
        addCategoryMenuItem.setOnAction(event -> onAddCategory());

        editNodeMenuItem.setText(I18n.get("categories.editNode"));
        editNodeMenuItem.setId("editNodeMenuItem");
        editNodeMenuItem.setOnAction(event -> openSelectedCategoryEditor());

        deleteNodeMenuItem.setText(I18n.get("categories.deleteNode"));
        deleteNodeMenuItem.setId("deleteNodeMenuItem");
        deleteNodeMenuItem.setOnAction(event -> deleteSelectedCategory());

        categoryContextMenu.getItems().setAll(addCategoryMenuItem, editNodeMenuItem, deleteNodeMenuItem);
    }

    private TreeCell<String> createCategoryCell(TreeView<String> treeView) {
        TreeCell<String> cell = new TreeCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
            }
        };

        cell.setOnMouseClicked(event -> {
            if (cell.isEmpty()) return;

            TreeItem<String> item = cell.getTreeItem();
            categoryTree.getSelectionModel().select(item);
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Long categoryId = findCategoryIdByItem(item);
                if (categoryId != null) {
                    showCategoryEditor(categoryId, null);
                }
            }
        });

        cell.setOnContextMenuRequested(event -> {
            if (cell.isEmpty()) return;

            TreeItem<String> item = cell.getTreeItem();
            categoryTree.getSelectionModel().select(item);
            Long categoryId = findCategoryIdByItem(item);
            addCategoryMenuItem.setDisable(categoryId != null && calculateDepth(categoryId) >= 5);
            editNodeMenuItem.setDisable(categoryId == null);
            deleteNodeMenuItem.setDisable(categoryId == null);
            categoryContextMenu.show(cell, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return cell;
    }

    private void loadTree() {
        itemByCategoryId.clear();

        TreeItem<String> root = new TreeItem<>(I18n.get("categories.root"));
        root.setExpanded(true);

        List<Category> allCategories = categoryService.getAllCategories();
        Map<Long, List<Category>> childrenMap = allCategories.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : -1L));

        buildTree(root, childrenMap.getOrDefault(-1L, List.of()), childrenMap);

        categoryTree.setRoot(root);
    }

    private void buildTree(TreeItem<String> parent, List<Category> children, Map<Long, List<Category>> childrenMap) {
        for (Category cat : children) {
            TreeItem<String> item = new TreeItem<>(cat.getName());
            parent.getChildren().add(item);
            itemByCategoryId.put(cat.getId(), item);

            List<Category> subChildren = childrenMap.getOrDefault(cat.getId(), List.of());
            if (!subChildren.isEmpty()) {
                buildTree(item, subChildren, childrenMap);
            }
        }
    }

    private Long findCategoryIdByItem(TreeItem<String> item) {
        for (Map.Entry<Long, TreeItem<String>> entry : itemByCategoryId.entrySet()) {
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
        TreeItem<String> selectedItem = categoryTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;

        Long parentId = findCategoryIdByItem(selectedItem);
        if (parentId != null && calculateDepth(parentId) >= 5) {
            showError(I18n.get("categories.maxDepth"));
            return;
        }

        showCategoryEditor(null, parentId);
    }

    private void openSelectedCategoryEditor() {
        TreeItem<String> selectedItem = categoryTree.getSelectionModel().getSelectedItem();
        Long categoryId = selectedItem == null ? null : findCategoryIdByItem(selectedItem);
        if (categoryId != null) {
            showCategoryEditor(categoryId, null);
        }
    }

    private void deleteSelectedCategory() {
        TreeItem<String> selectedItem = categoryTree.getSelectionModel().getSelectedItem();
        Long categoryId = selectedItem == null ? null : findCategoryIdByItem(selectedItem);
        if (categoryId != null) {
            confirmDeleteCategory(categoryId);
        }
    }

    private void showCategoryEditor(Long categoryId, Long parentId) {
        Category category = categoryId == null
                ? null
                : categoryService.getCategory(categoryId).orElse(null);
        if (categoryId != null && category == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
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
        TreeItem<String> item = itemByCategoryId.get(categoryId);
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
        alert.setTitle(I18n.get("categories.delete.title"));
        alert.initOwner(rootPane.getScene().getWindow());

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        VBox content = new VBox(10, messageLabel);
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

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Error");
        alert.initOwner(rootPane.getScene().getWindow());
        alert.showAndWait();
    }
}
