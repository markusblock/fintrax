package org.fintrax.ui.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.*;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.control.TableCellTooltips;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
public class RulesController {
    private static final String DISABLED_RULE_ROW_CLASS = "disabled-rule-row";

    @FXML
    private BorderPane rootPane;
    @FXML
    private TableView<Rule> ruleTable;
    @FXML
    private TextField ruleSearchField;
    @FXML
    private Button ruleSearchClearButton;
    @FXML
    private TableColumn<Rule, Number> ruleNumberColumn;
    @FXML
    private TableColumn<Rule, String> ruleNameColumn;
    @FXML
    private RuleEditorController ruleEditorController;
    @FXML
    private Button saveButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button previewButton;
    @FXML
    private Button applyAllButton;
    @FXML
    private javafx.scene.control.Label matchCountLabel;

    private final RuleService ruleService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final SettingsService settingsService;

    private final ObservableList<Rule> ruleData = FXCollections.observableArrayList();
    private Long editingRuleId;
    private boolean editingNew = false;

    public RulesController(RuleService ruleService, AccountService accountService,
                           CategoryService categoryService, LabelService labelService,
                           SettingsService settingsService) {
        this.ruleService = ruleService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.settingsService = settingsService;
    }

    @FXML
    public void initialize() {
        log.info("RulesController initialized");
        ruleNumberColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getPriority()));
        ruleNameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getName()));
        ruleNumberColumn.setSortable(true);
        ruleNameColumn.setSortable(true);
        ruleNameColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        FilteredList<Rule> filteredRules = new FilteredList<>(ruleData);
        ruleSearchField.textProperty().addListener((observable, oldValue, newValue) ->
                filteredRules.setPredicate(ruleNamePredicate(newValue)));
        ruleSearchClearButton.visibleProperty().bind(ruleSearchField.textProperty().isNotEmpty());
        ruleSearchClearButton.managedProperty().bind(ruleSearchClearButton.visibleProperty());
        SortedList<Rule> sortedRules = new SortedList<>(filteredRules);
        sortedRules.comparatorProperty().bind(ruleTable.comparatorProperty());
        ruleTable.setItems(sortedRules);
        ruleNumberColumn.setSortType(TableColumn.SortType.ASCENDING);
        ruleTable.getSortOrder().setAll(ruleNumberColumn);
        ruleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableCellTooltips.install(ruleTable);
        ruleTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(Rule item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("");
                getStyleClass().remove(DISABLED_RULE_ROW_CLASS);
                if (empty || item == null) {
                    setContextMenu(null);
                } else {
                    if (!item.isEnabled()) {
                        getStyleClass().add(DISABLED_RULE_ROW_CLASS);
                    }
                    setContextMenu(createRuleContextMenu(item));
                }
            }
        });
        loadRules();

        ruleTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !editingNew) {
                loadRuleEditor(newVal);
            }
        });
    }

    private void loadRules() {
        ruleData.setAll(ruleService.getAllRules());
    }

    @FXML
    private void onClearRuleSearch() {
        ruleSearchField.clear();
        ruleSearchField.requestFocus();
    }

    private Predicate<Rule> ruleNamePredicate(String searchText) {
        String query = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        return rule -> query.isEmpty() || rule.getName().toLowerCase(Locale.ROOT).contains(query);
    }

    private ContextMenu createRuleContextMenu(Rule rule) {
        MenuItem applyRule = new MenuItem(I18n.get("rules.applySelected"));
        applyRule.setGraphic(icon("fth-play", 14));
        applyRule.setDisable(!rule.isEnabled());
        applyRule.setOnAction(event -> new RuleReviewDialog(rootPane.getScene().getWindow(),
                new RuleReviewDialog.Services(ruleService, accountService, categoryService, labelService,
                        settingsService),
                onlyFillMissing -> ruleService.previewSingleRule(rule.getId(), onlyFillMissing), true).show());

        int ruleIndex = indexOfRule(rule.getId());
        MenuItem moveUp = new MenuItem(I18n.get("button.moveUp"));
        moveUp.setGraphic(icon("fth-arrow-up", 14));
        moveUp.setDisable(ruleIndex <= 0);
        moveUp.setOnAction(event -> moveRule(rule, -1));
        MenuItem moveDown = new MenuItem(I18n.get("button.moveDown"));
        moveDown.setGraphic(icon("fth-arrow-down", 14));
        moveDown.setDisable(ruleIndex < 0 || ruleIndex >= ruleData.size() - 1);
        moveDown.setOnAction(event -> moveRule(rule, 1));

        MenuItem toggleEnabled = new MenuItem(I18n.get(rule.isEnabled() ? "rules.disable" : "rules.enable"));
        toggleEnabled.setGraphic(icon("fth-toggle-right", 14));
        toggleEnabled.setOnAction(event -> toggleRule(rule));

        MenuItem deleteRule = new MenuItem(I18n.get("button.delete"));
        deleteRule.setGraphic(icon("fth-trash-2", 14));
        deleteRule.setOnAction(event -> confirmDeleteRule(rule));

        return new ContextMenu(applyRule, new SeparatorMenuItem(), moveUp, moveDown,
                toggleEnabled, new SeparatorMenuItem(), deleteRule);
    }

    private void selectRule(Rule rule) {
        editingNew = false;
        Rule selected = ruleTable.getSelectionModel().getSelectedItem();
        if (selected != null && Objects.equals(selected.getId(), rule.getId())) {
            loadRuleEditor(rule);
        } else {
            ruleTable.getSelectionModel().select(rule);
        }
    }

    private int indexOfRule(Long ruleId) {
        for (int index = 0; index < ruleData.size(); index++) {
            if (Objects.equals(ruleData.get(index).getId(), ruleId)) {
                return index;
            }
        }
        return -1;
    }

    private void loadRuleEditor(Rule rule) {
        editingRuleId = rule.getId();
        editingNew = false;

        ruleEditorController.setRule(rule.getName(), rule.getConditions(), rule.getActions());

        int matchCount = ruleService.previewRule(rule.getId());
        matchCountLabel.setText(I18n.get("rules.matchCount", matchCount));

        saveButton.setDisable(false);
        deleteButton.setDisable(false);
        previewButton.setDisable(false);
    }

    @FXML
    private void onAddRule() {
        editingNew = true;
        editingRuleId = null;
        ruleEditorController.prepareNewRule();
        matchCountLabel.setText("");
        saveButton.setDisable(false);
        deleteButton.setDisable(true);
        previewButton.setDisable(true);
    }

    @FXML
    private void onSave() {
        String name = ruleEditorController.getRuleName();
        if (name.isEmpty()) {
            showError(I18n.get("rules.name.required"));
            return;
        }

        List<RuleCondition> conditions = ruleEditorController.getConditions();
        List<RuleAction> actions = ruleEditorController.getActions();

        if (conditions.isEmpty()) {
            showError(I18n.get("rules.conditions.required"));
            return;
        }
        if (actions.isEmpty()) {
            showError(I18n.get("rules.actions.required"));
            return;
        }

        if (editingNew) {
            ruleService.createRule(name, conditions, actions);
            editingNew = false;
        } else if (editingRuleId != null) {
            ruleService.updateRule(editingRuleId, name, conditions, actions);
        }

        loadRules();
        log.info("Saved rule: {}", name);
    }

    @FXML
    private void onDelete() {
        if (editingRuleId == null) return;
        ruleService.getRule(editingRuleId).ifPresent(this::confirmDeleteRule);
    }

    private void confirmDeleteRule(Rule rule) {
        Long ruleId = rule.getId();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.get("rules.delete.confirm"),
                ButtonType.OK, ButtonType.CANCEL);
        FintraxUI.configureDialog(alert);
        alert.initOwner(rootPane.getScene().getWindow());

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                ruleService.deleteRule(ruleId);
                if (Objects.equals(editingRuleId, ruleId)) {
                    editingRuleId = null;
                    editingNew = false;
                    clearEditor();
                }
                loadRules();
            }
        });
    }

    private void toggleRule(Rule rule) {
        Long ruleId = rule.getId();
        editingNew = false;
        ruleService.toggleRule(ruleId);
        loadRules();
        ruleData.stream().filter(candidate -> Objects.equals(candidate.getId(), ruleId))
                .findFirst().ifPresent(this::selectRule);
    }

    private void moveRule(Rule rule, int offset) {
        int index = indexOfRule(rule.getId());
        int destination = index + offset;
        if (index < 0 || destination < 0 || destination >= ruleData.size()) return;

        List<Long> ids = ruleData.stream().map(Rule::getId).collect(Collectors.toCollection(ArrayList::new));
        Collections.swap(ids, index, destination);
        ruleService.reorderRules(ids);
        loadRules();
        ruleData.stream().filter(candidate -> Objects.equals(candidate.getId(), rule.getId()))
                .findFirst().ifPresent(this::selectRule);
    }

    @FXML
    private void onMoveUp() {
        Rule selected = ruleTable.getSelectionModel().getSelectedItem();
        if (selected != null) moveRule(selected, -1);
    }

    @FXML
    private void onMoveDown() {
        Rule selected = ruleTable.getSelectionModel().getSelectedItem();
        if (selected != null) moveRule(selected, 1);
    }

    @FXML
    private void onPreview() {
        Rule selected = ruleTable.getSelectionModel().getSelectedItem();
        if (selected != null) selectRule(selected);
    }

    @FXML
    private void onApplyAll() {
        new RuleReviewDialog(rootPane.getScene().getWindow(),
                new RuleReviewDialog.Services(ruleService, accountService, categoryService, labelService,
                        settingsService),
                ruleService::previewAllRules, false).show();
    }

    private void clearEditor() {
        ruleEditorController.clear();
        matchCountLabel.setText("");
        saveButton.setDisable(true);
        deleteButton.setDisable(true);
        previewButton.setDisable(true);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        FintraxUI.configureDialog(alert);
        alert.setTitle("Error");
        alert.initOwner(rootPane.getScene().getWindow());
        alert.showAndWait();
    }

    private static FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(literal);
        icon.setIconSize(size);
        return icon;
    }
}
