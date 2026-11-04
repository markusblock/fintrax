package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fintrax.config.I18n;
import org.fintrax.model.ActionType;
import org.fintrax.model.Category;
import org.fintrax.model.MatchField;
import org.fintrax.model.Operator;
import org.fintrax.model.RuleAction;
import org.fintrax.model.RuleCondition;
import org.fintrax.model.Transaction;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.ui.control.SearchableChoiceButton;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
@Scope("prototype")
public class RuleEditorController {
    private static final String DEFAULT_LABEL_COLOR = "#1976d2";

    @FXML
    private TextField ruleNameField;
    @FXML
    private VBox conditionsContainer;
    @FXML
    private VBox actionsContainer;

    private final CategoryService categoryService;
    private final LabelService labelService;
    private final List<ConditionRow> conditionRows = new ArrayList<>();
    private final List<ActionRow> actionRows = new ArrayList<>();
    private Runnable changeListener = () -> {};

    public RuleEditorController(CategoryService categoryService, LabelService labelService) {
        this.categoryService = categoryService;
        this.labelService = labelService;
    }

    @FXML
    private void initialize() {
        ruleNameField.setPromptText(I18n.get("dialog.createRule.name"));
        ruleNameField.textProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
    }

    public void setOnChange(Runnable listener) {
        changeListener = Objects.requireNonNull(listener);
        notifyChanged();
    }

    public void setRule(String name, List<RuleCondition> conditions, List<RuleAction> actions) {
        clear();
        ruleNameField.setText(name == null ? "" : name);
        conditions.forEach(this::addConditionRow);
        actions.stream().filter(action -> action.getActionType() != ActionType.SET_NOTE)
                .forEach(this::addActionRow);
        notifyChanged();
    }

    public void setFromTransaction(Transaction transaction) {
        String payee = transaction.getPayeeDisplay() != null
                ? transaction.getPayeeDisplay() : transaction.getOriginalPayee();
        String name = payee == null || payee.isBlank()
                ? I18n.get("dialog.createRule.defaultName")
                : I18n.get("dialog.createRule.defaultNameForPayee", payee);

        List<RuleCondition> conditions = new ArrayList<>();
        if (payee != null && !payee.isBlank()) {
            conditions.add(condition(MatchField.PAYEE_NAME, payee));
        }
        if (transaction.getPurpose() != null && !transaction.getPurpose().isBlank()) {
            conditions.add(condition(MatchField.PURPOSE, transaction.getPurpose()));
        }

        List<RuleAction> actions = new ArrayList<>();
        if (transaction.getCategoryId() != null) {
            actions.add(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                    .categoryId(transaction.getCategoryId()).build());
        }
        if (transaction.getLabelIds() != null) {
            transaction.getLabelIds().forEach(labelId -> actions.add(RuleAction.builder()
                    .actionType(ActionType.ADD_LABEL).labelId(labelId).build()));
        }

        setRule(name, conditions, actions);
        if (conditionRows.isEmpty()) addConditionRow(null);
        notifyChanged();
    }

    public void clear() {
        ruleNameField.clear();
        conditionsContainer.getChildren().clear();
        conditionRows.clear();
        actionsContainer.getChildren().clear();
        actionRows.clear();
        notifyChanged();
    }

    public void prepareNewRule() {
        clear();
        addConditionRow(null);
        addActionRow(null);
    }

    public String getRuleName() {
        return ruleNameField.getText().trim();
    }

    public List<RuleCondition> getConditions() {
        List<RuleCondition> conditions = new ArrayList<>();
        for (ConditionRow row : conditionRows) {
            String field = row.fieldCombo.getValue();
            String operator = row.operatorCombo.getValue();
            String value = row.valueField.getText().trim();
            if (field == null || operator == null) continue;

            RuleCondition condition = RuleCondition.builder()
                    .matchField(MatchField.valueOf(field))
                    .operator(Operator.valueOf(operator))
                    .value(value.isEmpty() ? null : value)
                    .build();
            if (Operator.AMOUNT_RANGE.name().equals(operator)) {
                String[] parts = value.split("[;,]");
                if (parts.length > 0) condition.setAmountMin(parseAmount(parts[0].trim()));
                if (parts.length > 1) condition.setAmountMax(parseAmount(parts[1].trim()));
            }
            conditions.add(condition);
        }
        return conditions;
    }

    public List<RuleAction> getActions() {
        List<RuleAction> actions = new ArrayList<>();
        for (ActionRow row : actionRows) {
            String type = row.typeCombo.getValue();
            String target = row.targetCombo.getValue();
            if (type == null) continue;

            RuleAction action = RuleAction.builder().actionType(ActionType.valueOf(type)).build();
            if (ActionType.ASSIGN_CATEGORY.name().equals(type) && target != null) {
                categoryService.getAllCategories().stream()
                        .filter(category -> category.getName().equals(target))
                        .findFirst().ifPresent(category -> action.setCategoryId(category.getId()));
            } else if (ActionType.ADD_LABEL.name().equals(type) && target != null) {
                labelService.getAllLabels().stream()
                        .filter(label -> label.getName().equals(target))
                        .findFirst().ifPresent(label -> action.setLabelId(label.getId()));
            }
            actions.add(action);
        }
        return actions;
    }

    public boolean isValid() {
        long categoryActionCount = actionRows.stream()
                .filter(row -> ActionType.ASSIGN_CATEGORY.name().equals(row.typeCombo.getValue()))
                .count();
        return !getRuleName().isEmpty()
                && !conditionRows.isEmpty() && conditionRows.stream().allMatch(ConditionRow::isValid)
                && !actionRows.isEmpty() && actionRows.stream().allMatch(ActionRow::isValid)
                && categoryActionCount <= 1;
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
            row.valueField.setText(existing.getValue() == null ? "" : existing.getValue());
        }
        row.fieldCombo.valueProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
        row.operatorCombo.valueProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
        row.valueField.textProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
        row.removeButton.setOnAction(event -> {
            conditionsContainer.getChildren().remove(row.hBox);
            conditionRows.remove(row);
            notifyChanged();
        });
        conditionRows.add(row);
        conditionsContainer.getChildren().add(row.hBox);
        notifyChanged();
    }

    private void addActionRow(RuleAction existing) {
        ActionRow row = new ActionRow();
        row.typeCombo.getItems().setAll(Arrays.stream(ActionType.values())
                .filter(actionType -> actionType != ActionType.SET_NOTE).map(Enum::name).toList());
        row.typeCombo.setOnAction(event -> {
            onActionTypeChanged(row);
            notifyChanged();
        });
        if (existing != null) {
            row.typeCombo.setValue(existing.getActionType().name());
            onActionTypeChanged(row);
            if (existing.getCategoryId() != null) {
                categoryService.getCategory(existing.getCategoryId())
                        .ifPresent(category -> row.targetCombo.setValue(category.getName()));
            }
            if (existing.getLabelId() != null) {
                labelService.getLabel(existing.getLabelId())
                        .ifPresent(label -> row.targetCombo.setValue(label.getName()));
            }
        }
        row.targetCombo.valueProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
        row.removeButton.setOnAction(event -> {
            actionsContainer.getChildren().remove(row.hBox);
            actionRows.remove(row);
            notifyChanged();
        });
        actionRows.add(row);
        actionsContainer.getChildren().add(row.hBox);
        notifyChanged();
    }

    private void onActionTypeChanged(ActionRow row) {
        String type = row.typeCombo.getValue();
        row.targetCombo.setValue(null);
        row.targetCombo.setCreateChoice(null, null);
        row.targetCombo.setDisable(type == null);
        if (ActionType.ASSIGN_CATEGORY.name().equals(type)) {
            row.targetCombo.setCreateChoice("picker.category.create", name ->
                    categoryService.createCategory(name, null, null).getName());
            row.targetCombo.setChoices(categoryService.getAllCategories()
                    .stream().map(Category::getName).toList());
        } else if (ActionType.ADD_LABEL.name().equals(type)) {
            row.targetCombo.setCreateChoice("picker.labels.create", name ->
                    labelService.createLabel(name, DEFAULT_LABEL_COLOR, null).getName());
            row.targetCombo.setChoices(labelService.getAllLabels()
                    .stream().map(org.fintrax.model.Label::getName).toList());
        } else {
            row.targetCombo.setChoices(List.of());
        }
    }

    private void notifyChanged() {
        changeListener.run();
    }

    private static RuleCondition condition(MatchField field, String value) {
        return RuleCondition.builder().matchField(field).operator(Operator.CONTAINS).value(value).build();
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return new BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class ConditionRow {
        private final HBox hBox = new HBox(5);
        private final ComboBox<String> fieldCombo = new ComboBox<>();
        private final ComboBox<String> operatorCombo = new ComboBox<>();
        private final TextField valueField = new TextField();
        private final Button removeButton = removeButton();

        private ConditionRow() {
            hBox.getStyleClass().add("rule-condition-row");
            fieldCombo.setPromptText("Field");
            operatorCombo.setPromptText("Operator");
            valueField.setPromptText("Value");
            hBox.getChildren().addAll(fieldCombo, operatorCombo, valueField, removeButton);
        }

        private boolean isValid() {
            return fieldCombo.getValue() != null && operatorCombo.getValue() != null
                    && !valueField.getText().isBlank();
        }
    }

    private static final class ActionRow {
        private final HBox hBox = new HBox(5);
        private final ComboBox<String> typeCombo = new ComboBox<>();
        private final SearchableChoiceButton targetCombo = new SearchableChoiceButton();
        private final Button removeButton = removeButton();

        private ActionRow() {
            hBox.getStyleClass().add("rule-action-row");
            typeCombo.setPromptText("Action");
            targetCombo.setPromptText("Target");
            targetCombo.setDisable(true);
            HBox.setHgrow(targetCombo, Priority.ALWAYS);
            hBox.getChildren().addAll(typeCombo, targetCombo, removeButton);
        }

        private boolean isValid() {
            return typeCombo.getValue() != null && targetCombo.getValue() != null;
        }
    }

    private static Button removeButton() {
        Button button = new Button();
        FontIcon icon = new FontIcon("fth-x");
        icon.setIconSize(14);
        button.setGraphic(icon);
        button.getStyleClass().addAll("icon-button", "danger");
        button.setAccessibleText(I18n.get("button.delete"));
        button.setTooltip(new Tooltip(I18n.get("button.delete")));
        return button;
    }
}
