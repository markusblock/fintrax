package org.fintrax.ui.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.*;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.control.TableColumnVisibilityMenu;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
public class RulesController {
    private static final String DISABLED_RULE_ROW_CLASS = "disabled-rule-row";
    private static final String COLUMN_CONFIG_KEY = "rules.columns";

    @FXML
    private BorderPane rootPane;
    @FXML
    private TableView<Rule> ruleTable;
    @FXML
    private MenuButton columnMenuButton;
    @FXML
    private TableColumn<Rule, Number> ruleNumberColumn;
    @FXML
    private TableColumn<Rule, String> ruleNameColumn;
    @FXML
    private TextField ruleNameField;
    @FXML
    private VBox conditionsContainer;
    @FXML
    private VBox actionsContainer;
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
    private final List<ConditionRow> conditionRows = new ArrayList<>();
    private final List<ActionRow> actionRows = new ArrayList<>();

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
        ruleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ruleNumberColumn.setSortable(false);
        ruleNameColumn.setSortable(false);
        setupColumnMenu();
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

    private void setupColumnMenu() {
        TableColumnVisibilityMenu.install(ruleTable, columnMenuButton, COLUMN_CONFIG_KEY, settingsService,
                List.of(new TableColumnVisibilityMenu.Column("number", ruleNumberColumn),
                        new TableColumnVisibilityMenu.Column("name", ruleNameColumn)));
    }

    private void loadRules() {
        ruleData.setAll(ruleService.getAllRules());
        ruleTable.setItems(ruleData);
    }

    private ContextMenu createRuleContextMenu(Rule rule) {
        MenuItem applyRule = new MenuItem(I18n.get("rules.applySelected"));
        applyRule.setGraphic(icon("fth-play", 14));
        applyRule.setDisable(!rule.isEnabled());
        applyRule.setOnAction(event -> new RuleReviewDialog(rootPane.getScene().getWindow(), ruleService,
                accountService, categoryService, labelService, settingsService,
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

        ruleNameField.setText(rule.getName() != null ? rule.getName() : "");
        ruleNameField.setDisable(false);

        conditionsContainer.getChildren().clear();
        conditionRows.clear();
        for (RuleCondition cond : rule.getConditions()) {
            addConditionRow(cond);
        }

        actionsContainer.getChildren().clear();
        actionRows.clear();
        for (RuleAction action : rule.getActions()) {
            if (action.getActionType() != ActionType.SET_NOTE) {
                addActionRow(action);
            }
        }

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
        ruleNameField.clear();
        ruleNameField.setDisable(false);
        conditionsContainer.getChildren().clear();
        conditionRows.clear();
        actionsContainer.getChildren().clear();
        actionRows.clear();
        matchCountLabel.setText("");
        saveButton.setDisable(false);
        deleteButton.setDisable(true);
        previewButton.setDisable(true);
        addConditionRow(null);
        addActionRow(null);
    }

    @FXML
    private void onAddCondition() {
        addConditionRow(null);
    }

    @FXML
    private void onAddAction() {
        addActionRow(null);
    }

    private void addConditionRow(RuleCondition existing) {
        ConditionRow row = new ConditionRow();
        row.fieldCombo.getItems().setAll(Arrays.stream(MatchField.values()).map(Enum::name).toList());
        row.operatorCombo.getItems().setAll(Arrays.stream(Operator.values()).map(Enum::name).toList());

        if (existing != null) {
            row.fieldCombo.setValue(existing.getMatchField().name());
            row.operatorCombo.setValue(existing.getOperator().name());
            row.valueField.setText(existing.getValue() != null ? existing.getValue() : "");
        }

        row.removeButton.setOnAction(e -> {
            conditionsContainer.getChildren().remove(row.hBox);
            conditionRows.remove(row);
        });

        conditionRows.add(row);
        conditionsContainer.getChildren().add(row.hBox);
    }

    private void addActionRow(RuleAction existing) {
        ActionRow row = new ActionRow();
        row.typeCombo.getItems().setAll(Arrays.stream(ActionType.values())
                .filter(actionType -> actionType != ActionType.SET_NOTE).map(Enum::name).toList());

        if (existing != null) {
            row.typeCombo.setValue(existing.getActionType().name());
            if (existing.getCategoryId() != null) {
                categoryService.getCategory(existing.getCategoryId())
                        .ifPresent(c -> row.targetCombo.setValue(c.getName()));
            }
            if (existing.getLabelId() != null) {
                labelService.getLabel(existing.getLabelId())
                        .ifPresent(l -> row.targetCombo.setValue(l.getName()));
            }
            if (existing.getNote() != null) {
                row.targetCombo.setValue(existing.getNote());
            }
        }

        row.typeCombo.setOnAction(e -> onActionTypeChanged(row));
        onActionTypeChanged(row);

        row.removeButton.setOnAction(e -> {
            actionsContainer.getChildren().remove(row.hBox);
            actionRows.remove(row);
        });

        actionRows.add(row);
        actionsContainer.getChildren().add(row.hBox);
    }

    private void onActionTypeChanged(ActionRow row) {
        String type = row.typeCombo.getValue();
        row.targetCombo.getItems().clear();
        row.targetCombo.setDisable(false);

        if ("ASSIGN_CATEGORY".equals(type)) {
            row.targetCombo.getItems().setAll(
                    categoryService.getAllCategories().stream().map(Category::getName).toList());
        } else if ("ADD_LABEL".equals(type)) {
            row.targetCombo.getItems().setAll(
                    labelService.getAllLabels().stream().map(org.fintrax.model.Label::getName).toList());
        }
    }

    @FXML
    private void onSave() {
        String name = ruleNameField.getText().trim();
        if (name.isEmpty()) {
            showError(I18n.get("rules.name.required"));
            return;
        }

        List<RuleCondition> conditions = buildConditions();
        List<RuleAction> actions = buildActions();

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

    private List<RuleCondition> buildConditions() {
        List<RuleCondition> conditions = new ArrayList<>();
        for (ConditionRow row : conditionRows) {
            String field = row.fieldCombo.getValue();
            String operator = row.operatorCombo.getValue();
            String value = row.valueField.getText().trim();

            if (field == null || operator == null) continue;

            RuleCondition cond = RuleCondition.builder()
                    .matchField(MatchField.valueOf(field))
                    .operator(Operator.valueOf(operator))
                    .value(value.isEmpty() ? null : value)
                    .build();

            if (Operator.AMOUNT_RANGE.name().equals(operator)) {
                String[] parts = value.split("[;,]");
                if (parts.length >= 1) cond.setAmountMin(parseAmount(parts[0].trim()));
                if (parts.length >= 2) cond.setAmountMax(parseAmount(parts[1].trim()));
            }

            conditions.add(cond);
        }
        return conditions;
    }

    private List<RuleAction> buildActions() {
        List<RuleAction> actions = new ArrayList<>();
        for (ActionRow row : actionRows) {
            String type = row.typeCombo.getValue();
            String target = row.targetCombo.getValue();
            if (type == null) continue;

            RuleAction action = RuleAction.builder()
                    .actionType(ActionType.valueOf(type))
                    .build();

            if ("ASSIGN_CATEGORY".equals(type) && target != null) {
                categoryService.getAllCategories().stream()
                        .filter(c -> c.getName().equals(target))
                        .findFirst()
                        .ifPresent(c -> action.setCategoryId(c.getId()));
            } else if ("ADD_LABEL".equals(type) && target != null) {
                labelService.getAllLabels().stream()
                        .filter(l -> l.getName().equals(target))
                        .findFirst()
                        .ifPresent(l -> action.setLabelId(l.getId()));
            }

            actions.add(action);
        }
        return actions;
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return new BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
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
        new RuleReviewDialog(rootPane.getScene().getWindow(), ruleService, accountService,
                categoryService, labelService, settingsService,
                ruleService::previewAllRules, false).show();
    }

    private void clearEditor() {
        ruleNameField.clear();
        conditionsContainer.getChildren().clear();
        conditionRows.clear();
        actionsContainer.getChildren().clear();
        actionRows.clear();
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

    private static class ConditionRow {
        HBox hBox = new HBox(5);
        ComboBox<String> fieldCombo = new ComboBox<>();
        ComboBox<String> operatorCombo = new ComboBox<>();
        TextField valueField = new TextField();
        Button removeButton = new Button("\u2716");

        ConditionRow() {
            hBox.getStyleClass().add("rule-condition-row");
            removeButton.setText("");
            removeButton.setGraphic(icon("fth-x", 14));
            removeButton.getStyleClass().addAll("icon-button", "danger");
            removeButton.setAccessibleText(I18n.get("button.delete"));
            removeButton.setTooltip(new Tooltip(I18n.get("button.delete")));
            fieldCombo.setPromptText("Field");
            operatorCombo.setPromptText("Operator");
            valueField.setPromptText("Value");
            hBox.getChildren().addAll(fieldCombo, operatorCombo, valueField, removeButton);
        }
    }

    private static class ActionRow {
        HBox hBox = new HBox(5);
        ComboBox<String> typeCombo = new ComboBox<>();
        ComboBox<String> targetCombo = new ComboBox<>();
        Button removeButton = new Button("\u2716");

        ActionRow() {
            hBox.getStyleClass().add("rule-action-row");
            removeButton.setText("");
            removeButton.setGraphic(icon("fth-x", 14));
            removeButton.getStyleClass().addAll("icon-button", "danger");
            removeButton.setAccessibleText(I18n.get("button.delete"));
            removeButton.setTooltip(new Tooltip(I18n.get("button.delete")));
            typeCombo.setPromptText("Action");
            targetCombo.setPromptText("Target");
            hBox.getChildren().addAll(typeCombo, targetCombo, removeButton);
        }
    }

    private static FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(literal);
        icon.setIconSize(size);
        return icon;
    }
}
