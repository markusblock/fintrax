package org.fintrax.ui.controller;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import org.fintrax.config.I18n;
import org.fintrax.model.Category;
import org.fintrax.model.Transaction;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.TransactionService;
import org.fintrax.ui.control.SearchableListPicker;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

        MenuItem setLabelsItem = new MenuItem(I18n.get("contextMenu.setLabels"));
        setLabelsItem.setGraphic(icon("fth-plus", 14));
        setLabelsItem.setOnAction(event -> setLabels(table, owner, afterEdit));

        ContextMenu contextMenu = new ContextMenu(setCategoryItem, setLabelsItem);
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

        String title = I18n.get("dialog.bulkCategory.header", selected.size());
        Long selectedCategoryId = selected.get(0).getCategoryId();
        boolean sameCategory = selected.stream()
                .allMatch(transaction -> java.util.Objects.equals(selectedCategoryId, transaction.getCategoryId()));
        Category initiallySelected = sameCategory && selectedCategoryId != null
                ? categoryService.getCategory(selectedCategoryId).orElse(null)
                : null;
        SearchableListPicker.chooseOne(owner.getScene().getWindow(), title,
                categoryService.getAllCategories(), initiallySelected,
                Category::getName,
                "picker.category.create", name -> categoryService.createCategory(name, null, null))
                .ifPresent(choice -> {
                    Long categoryId = choice.isEmpty() ? null : choice.get(0).getId();
                    transactionService.bulkUpdateCategory(
                            selected.stream().map(Transaction::getId).toList(), categoryId);
                    afterEdit.run();
                });
    }

    public void setLabels(TableView<Transaction> table, Node owner, Runnable afterEdit) {
        List<Transaction> selected = selectedTransactions(table);
        if (selected.isEmpty()) return;

        Set<Long> originalIds = selected.stream()
                .filter(transaction -> transaction.getLabelIds() != null)
                .flatMap(transaction -> transaction.getLabelIds().stream())
                .collect(Collectors.toCollection(HashSet::new));
        List<org.fintrax.model.Label> originalLabels = labelService.getAllLabels().stream()
                .filter(label -> originalIds.contains(label.getId()))
                .toList();
        SearchableListPicker.chooseMany(owner.getScene().getWindow(),
                I18n.get("dialog.bulkLabel.header", selected.size()),
                labelService.getAllLabels(), originalLabels, org.fintrax.model.Label::getName,
                "picker.labels.create", name -> labelService.createLabel(name, "#1976d2", null))
                .ifPresent(chosenLabels -> {
                    Set<Long> chosenIds = chosenLabels.stream()
                            .map(org.fintrax.model.Label::getId)
                            .collect(Collectors.toSet());
                    List<Long> transactionIds = selected.stream().map(Transaction::getId).toList();
                    for (Long labelId : chosenIds) {
                        if (!originalIds.contains(labelId)) {
                            transactionService.bulkAddLabel(transactionIds, labelId);
                        }
                    }
                    for (Long labelId : originalIds) {
                        if (!chosenIds.contains(labelId)) {
                            transactionService.bulkRemoveLabel(transactionIds, labelId);
                        }
                    }
                    afterEdit.run();
                });
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
