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
import org.fintrax.ui.control.CategoryHierarchy;
import org.fintrax.ui.control.SearchableDropdownPicker;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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

    public void installContextMenu(TableView<Transaction> table, Runnable afterEdit,
                                   Consumer<Transaction> createRule) {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setRowFactory(view -> new TableRow<>() {
            private final ContextMenu contextMenu = createContextMenu(this, table, afterEdit, createRule);

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

    private ContextMenu createContextMenu(TableRow<Transaction> row, TableView<Transaction> table,
                                          Runnable afterEdit, Consumer<Transaction> createRule) {
        MenuItem setCategoryItem = new MenuItem(I18n.get("contextMenu.setCategory"));
        setCategoryItem.setGraphic(icon("fth-tag", 14));
        setCategoryItem.setOnAction(event -> setCategory(table, row, afterEdit));

        MenuItem setLabelsItem = new MenuItem(I18n.get("contextMenu.setLabels"));
        setLabelsItem.setGraphic(icon("fth-plus", 14));
        setLabelsItem.setOnAction(event -> setLabels(table, row, afterEdit));

        List<MenuItem> items = new ArrayList<>(List.of(setCategoryItem, setLabelsItem));
        if (createRule != null) {
            MenuItem createRuleItem = new MenuItem(I18n.get("contextMenu.createRule"));
            createRuleItem.setGraphic(icon("fth-plus-circle", 14));
            createRuleItem.setOnAction(event -> {
                Transaction transaction = row.getItem();
                if (transaction != null) createRule.accept(transaction);
            });
            items.add(createRuleItem);
        }
        return new ContextMenu(items.toArray(MenuItem[]::new));
    }

    public void setCategory(TableView<Transaction> table, Node anchor, Runnable afterEdit) {
        List<Transaction> selected = selectedTransactions(table);
        if (selected.isEmpty()) return;

        Long selectedCategoryId = selected.get(0).getCategoryId();
        boolean sameCategory = selected.stream()
                .allMatch(transaction -> java.util.Objects.equals(selectedCategoryId, transaction.getCategoryId()));
        Category initiallySelected = sameCategory && selectedCategoryId != null
                ? categoryService.getCategory(selectedCategoryId).orElse(null)
                : null;
        List<Category> categories = categoryService.getAllCategories();
        SearchableDropdownPicker.showOne(anchor, categories, initiallySelected,
                CategoryHierarchy.displayPaths(categories), "picker.category.create",
                name -> categoryService.createCategory(name, null, null), choice -> {
                    transactionService.bulkUpdateCategory(selected.stream().map(Transaction::getId).toList(),
                            choice == null ? null : choice.getId());
                    afterEdit.run();
                });
    }

    public void setLabels(TableView<Transaction> table, Node anchor, Runnable afterEdit) {
        List<Transaction> selected = selectedTransactions(table);
        if (selected.isEmpty()) return;

        Set<Long> originalIds = selected.stream()
                .filter(transaction -> transaction.getLabelIds() != null)
                .flatMap(transaction -> transaction.getLabelIds().stream())
                .collect(Collectors.toCollection(HashSet::new));
        List<org.fintrax.model.Label> originalLabels = labelService.getAllLabels().stream()
                .filter(label -> originalIds.contains(label.getId()))
                .toList();
        SearchableDropdownPicker.showMany(anchor, labelService.getAllLabels(), originalLabels,
                org.fintrax.model.Label::getName, "picker.labels.create",
                name -> labelService.createLabel(name, "#1976d2", null), chosenLabels -> {
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
