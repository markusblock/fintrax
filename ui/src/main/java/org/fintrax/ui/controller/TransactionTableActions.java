package org.fintrax.ui.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SearchableComboBox;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.TransactionService;
import org.fintrax.ui.FintraxUI;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class TransactionTableActions {
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final LabelService labelService;

    public TransactionTableActions(TransactionService transactionService,
                                   CategoryService categoryService,
                                   LabelService labelService) {
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.labelService = labelService;
    }

    public void installContextMenu(TableView<Transaction> table, Node owner, Runnable afterEdit) {
        MenuItem setCategoryItem = new MenuItem(I18n.get("contextMenu.setCategory"));
        setCategoryItem.setGraphic(icon("fth-tag", 14));
        setCategoryItem.setOnAction(event -> setCategory(table, owner, afterEdit));

        MenuItem addLabelItem = new MenuItem(I18n.get("contextMenu.addLabel"));
        addLabelItem.setGraphic(icon("fth-plus", 14));
        addLabelItem.setOnAction(event -> addLabel(table, owner, afterEdit));

        MenuItem removeLabelItem = new MenuItem(I18n.get("contextMenu.removeLabel"));
        removeLabelItem.setGraphic(icon("fth-minus", 14));
        removeLabelItem.setOnAction(event -> removeLabel(table, owner, afterEdit));

        ContextMenu contextMenu = new ContextMenu(setCategoryItem, addLabelItem, removeLabelItem);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setRowFactory(view -> new TableRow<>() {
            {
                setOnMousePressed(event -> {
                    if (event.getButton() == MouseButton.SECONDARY && !isEmpty()
                            && !table.getSelectionModel().isSelected(getIndex())) {
                        table.getSelectionModel().clearAndSelect(getIndex());
                    }
                });
            }

            @Override
            protected void updateItem(Transaction item, boolean empty) {
                super.updateItem(item, empty);
                setContextMenu(empty ? null : contextMenu);
            }
        });
    }

    public void setCategory(TableView<Transaction> table, Node owner, Runnable afterEdit) {
        List<Transaction> selected = selectedTransactions(table);
        if (selected.isEmpty()) return;

        SearchableComboBox<Category> categoryCombo = createSearchableComboBox(
                categoryService.getAllCategories(), Category::getName);
        categoryCombo.setId("categorySearchCombo");

        Dialog<Category> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.setTitle(I18n.get("dialog.bulkCategory.title"));
        dialog.setHeaderText(I18n.get("dialog.bulkCategory.header", selected.size()));
        dialog.initOwner(owner.getScene().getWindow());

        DialogPane dialogPane = dialog.getDialogPane();
        VBox content = new VBox(8, new Label(I18n.get("dialog.bulkCategory.prompt")), categoryCombo);
        content.setMinWidth(320);
        dialogPane.setContent(content);
        ButtonType applyType = ButtonType.OK;
        dialogPane.getButtonTypes().setAll(applyType, ButtonType.CANCEL);
        Button applyButton = (Button) dialogPane.lookupButton(applyType);
        ((Button) dialogPane.lookupButton(ButtonType.CANCEL)).setId("cancelCategoryButton");
        applyButton.setDisable(categoryCombo.getValue() == null);
        categoryCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                applyButton.setDisable(newValue == null));
        dialog.setResultConverter(buttonType -> buttonType == applyType ? categoryCombo.getValue() : null);
        dialog.setOnShown(event -> Platform.runLater(categoryCombo::show));

        dialog.showAndWait().ifPresent(category -> {
            transactionService.bulkUpdateCategory(
                    selected.stream().map(Transaction::getId).toList(), category.getId());
            afterEdit.run();
        });
    }

    public void addLabel(TableView<Transaction> table, Node owner, Runnable afterEdit) {
        showLabelDialog(table, owner, afterEdit, true);
    }

    public void removeLabel(TableView<Transaction> table, Node owner, Runnable afterEdit) {
        showLabelDialog(table, owner, afterEdit, false);
    }

    private void showLabelDialog(TableView<Transaction> table, Node owner, Runnable afterEdit, boolean adding) {
        List<Transaction> selected = selectedTransactions(table);
        if (selected.isEmpty()) return;

        SearchableComboBox<org.fintrax.model.Label> labelCombo = createSearchableComboBox(
                labelService.getAllLabels(), org.fintrax.model.Label::getName);
        labelCombo.setId("labelSearchCombo");

        Dialog<org.fintrax.model.Label> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.setTitle(I18n.get("dialog.bulkLabel.title"));
        String headerKey = adding ? "dialog.bulkLabelAdd.header" : "dialog.bulkLabelRemove.header";
        dialog.setHeaderText(I18n.get(headerKey, selected.size()));
        dialog.initOwner(owner.getScene().getWindow());

        DialogPane dialogPane = dialog.getDialogPane();
        VBox content = new VBox(8, new Label(I18n.get("dialog.bulkLabel.prompt")), labelCombo);
        content.setMinWidth(320);
        dialogPane.setContent(content);
        ButtonType applyType = ButtonType.OK;
        dialogPane.getButtonTypes().setAll(applyType, ButtonType.CANCEL);
        Button applyButton = (Button) dialogPane.lookupButton(applyType);
        ((Button) dialogPane.lookupButton(ButtonType.CANCEL)).setId("cancelLabelButton");
        applyButton.setDisable(labelCombo.getValue() == null);
        labelCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                applyButton.setDisable(newValue == null));
        dialog.setResultConverter(buttonType -> buttonType == applyType ? labelCombo.getValue() : null);
        dialog.setOnShown(event -> Platform.runLater(labelCombo::show));

        dialog.showAndWait().ifPresent(label -> {
            List<Long> transactionIds = selected.stream().map(Transaction::getId).toList();
            if (adding) {
                transactionService.bulkAddLabel(transactionIds, label.getId());
            } else {
                transactionService.bulkRemoveLabel(transactionIds, label.getId());
            }
            afterEdit.run();
        });
    }

    private <T> SearchableComboBox<T> createSearchableComboBox(List<T> items, Function<T, String> displayText) {
        SearchableComboBox<T> combo = new SearchableComboBox<>(FXCollections.observableArrayList(items));
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : displayText.apply(item);
            }

            @Override
            public T fromString(String value) {
                return combo.getItems().stream()
                        .filter(item -> displayText.apply(item).equals(value))
                        .findFirst()
                        .orElse(null);
            }
        });
        combo.setCellFactory(list -> createSearchableCell(displayText));
        combo.setButtonCell(createSearchableCell(displayText));
        combo.getSelectionModel().selectFirst();
        return combo;
    }

    private <T> ListCell<T> createSearchableCell(Function<T, String> displayText) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayText.apply(item));
            }
        };
    }

    private List<Transaction> selectedTransactions(TableView<Transaction> table) {
        return new ArrayList<>(table.getSelectionModel().getSelectedItems());
    }

    private FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
