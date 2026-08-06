package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.service.CategoryService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
public class CategoriesController {
    @FXML
    private BorderPane rootPane;
    @FXML
    private TreeView<String> categoryTree;
    @FXML
    private TextField nameField;
    @FXML
    private TextField colorField;
    @FXML
    private TextField commentField;
    @FXML
    private Button saveButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button addButton;
    @FXML
    private Label depthLabel;

    private final CategoryService categoryService;
    private final Map<Long, TreeItem<String>> itemByCategoryId = new HashMap<>();
    private Long selectedCategoryId;
    private boolean editingNew = false;

    public CategoriesController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @FXML
    public void initialize() {
        log.info("CategoriesController initialized");
        loadTree();

        categoryTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null) {
                onTreeItemSelected(newVal);
            }
        });
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

    private void onTreeItemSelected(TreeItem<String> item) {
        if (editingNew) return;

        Long catId = findCategoryIdByItem(item);
        if (catId == null) {
            clearEditor();
            return;
        }

        selectedCategoryId = catId;
        categoryService.getCategory(catId).ifPresent(cat -> {
            nameField.setText(cat.getName() != null ? cat.getName() : "");
            colorField.setText(cat.getColor() != null ? cat.getColor() : "");
            commentField.setText(cat.getComment() != null ? cat.getComment() : "");
            deleteButton.setDisable(false);
            saveButton.setDisable(false);
            updateDepthLabel(catId);
        });
    }

    private Long findCategoryIdByItem(TreeItem<String> item) {
        for (Map.Entry<Long, TreeItem<String>> entry : itemByCategoryId.entrySet()) {
            if (entry.getValue() == item) return entry.getKey();
        }
        return null;
    }

    private void updateDepthLabel(Long categoryId) {
        int depth = calculateDepth(categoryId);
        depthLabel.setText(I18n.get("categories.depth", depth));
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

    @FXML
    private void onAddChild() {
        Long parentId = selectedCategoryId;
        if (parentId != null && calculateDepth(parentId) >= 5) {
            showError(I18n.get("categories.maxDepth"));
            return;
        }

        editingNew = true;
        clearEditor();
        nameField.setDisable(false);
        colorField.setDisable(false);
        commentField.setDisable(false);
        saveButton.setDisable(false);
        deleteButton.setDisable(true);
        nameField.requestFocus();
    }

    @FXML
    private void onAddRoot() {
        editingNew = true;
        selectedCategoryId = null;
        clearEditor();
        nameField.setDisable(false);
        colorField.setDisable(false);
        commentField.setDisable(false);
        saveButton.setDisable(false);
        deleteButton.setDisable(true);
        nameField.requestFocus();
    }

    @FXML
    private void onSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError(I18n.get("categories.name.required"));
            return;
        }

        String color = colorField.getText().trim();
        String comment = commentField.getText().trim();

        if (editingNew) {
            categoryService.createCategory(name, selectedCategoryId,
                    color.isEmpty() ? null : color,
                    comment.isEmpty() ? null : comment);
            editingNew = false;
        } else if (selectedCategoryId != null) {
            categoryService.updateCategory(selectedCategoryId, name,
                    color.isEmpty() ? null : color,
                    comment.isEmpty() ? null : comment);
        }

        loadTree();
        clearEditor();
        log.info("Saved category");
    }

    @FXML
    private void onDelete() {
        if (selectedCategoryId == null) return;

        Category cat = categoryService.getCategory(selectedCategoryId).orElse(null);
        if (cat == null) return;

        List<Category> children = categoryService.getChildCategories(selectedCategoryId);
        String msg = children.isEmpty()
                ? I18n.get("categories.delete.confirm", cat.getName())
                : I18n.get("categories.delete.withChildren", cat.getName(), children.size());

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(I18n.get("categories.delete.title"));
        alert.initOwner(rootPane.getScene().getWindow());

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                categoryService.deleteCategory(selectedCategoryId, null);
                selectedCategoryId = null;
                loadTree();
                clearEditor();
            }
        });
    }

    private void clearEditor() {
        nameField.clear();
        colorField.clear();
        commentField.clear();
        depthLabel.setText("");
        if (!editingNew) {
            saveButton.setDisable(true);
            deleteButton.setDisable(true);
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Error");
        alert.initOwner(rootPane.getScene().getWindow());
        alert.showAndWait();
    }
}
