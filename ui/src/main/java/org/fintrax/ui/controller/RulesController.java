package org.fintrax.ui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RulesController {
    @FXML
    private BorderPane rootPane;
    @FXML
    private ListView<Rule> ruleList;
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

    private final RuleService ruleService = ServiceRegistry.getInstance().getRuleService();
    private final CategoryService categoryService = ServiceRegistry.getInstance().getCategoryService();
    private final LabelService labelService = ServiceRegistry.getInstance().getLabelService();

    private final ObservableList<Rule> ruleData = FXCollections.observableArrayList();
    private Long editingRuleId;
    private boolean editingNew = false;
    private final List<ConditionRow> conditionRows = new ArrayList<>();
    private final List<ActionRow> actionRows = new ArrayList<>();

    @FXML
    public void initialize() {
        log.info("RulesController initialized");
        loadRules();

        ruleList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !editingNew) {
                loadRuleEditor(newVal);
            }
        });
    }

    private void loadRules() {
        ruleData.setAll(ruleService.getAllRules());
        ruleList.setItems(ruleData);
        ruleList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Rule item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String status = item.isEnabled() ? "\u2705" : "\u274C";
                    setText(status + "  " + item.getName() + "  (#" + item.getPriority() + ")");
                }
            }
        });
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
            addActionRow(action);
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
        row.typeCombo.getItems().setAll(Arrays.stream(ActionType.values()).map(Enum::name).toList());

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
        } else if ("SET_NOTE".equals(type)) {
            row.targetCombo.setDisable(false);
            row.targetCombo.setEditable(true);
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
            } else if ("SET_NOTE".equals(type)) {
                action.setNote(target);
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

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.get("rules.delete.confirm"),
                ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(rootPane.getScene().getWindow());

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                ruleService.deleteRule(editingRuleId);
                editingRuleId = null;
                loadRules();
                clearEditor();
            }
        });
    }

    @FXML
    private void onToggleEnabled() {
        Rule selected = ruleList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        ruleService.toggleRule(selected.getId());
        loadRules();
    }

    @FXML
    private void onMoveUp() {
        int idx = ruleList.getSelectionModel().getSelectedIndex();
        if (idx <= 0) return;

        List<Long> ids = ruleData.stream().map(Rule::getId).collect(Collectors.toCollection(ArrayList::new));
        Collections.swap(ids, idx, idx - 1);
        ruleService.reorderRules(ids);
        loadRules();
        ruleList.getSelectionModel().select(idx - 1);
    }

    @FXML
    private void onMoveDown() {
        int idx = ruleList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= ruleData.size() - 1) return;

        List<Long> ids = ruleData.stream().map(Rule::getId).collect(Collectors.toCollection(ArrayList::new));
        Collections.swap(ids, idx, idx + 1);
        ruleService.reorderRules(ids);
        loadRules();
        ruleList.getSelectionModel().select(idx + 1);
    }

    @FXML
    private void onPreview() {
        Rule selected = ruleList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int count = ruleService.previewRule(selected.getId());
        matchCountLabel.setText(I18n.get("rules.matchCount", count));
    }

    @FXML
    private void onApplyAll() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.get("rules.applyAll.confirm"),
                ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(rootPane.getScene().getWindow());

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                ruleService.applyAllRules();
                log.info("Applied all rules");
            }
        });
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
            typeCombo.setPromptText("Action");
            targetCombo.setPromptText("Target");
            hBox.getChildren().addAll(typeCombo, targetCombo, removeButton);
        }
    }
}
