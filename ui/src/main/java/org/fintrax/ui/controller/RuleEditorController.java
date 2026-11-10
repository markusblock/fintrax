package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.fintrax.config.I18n;
import org.fintrax.model.ActionType;
import org.fintrax.model.Category;
import org.fintrax.model.MatchField;
import org.fintrax.model.Operator;
import org.fintrax.model.RuleAction;
import org.fintrax.model.RuleCondition;
import org.fintrax.model.RuleMatchMode;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
@Scope("prototype")
public class RuleEditorController {
    private static final String DEFAULT_LABEL_COLOR = "#1976d2";
    private static final String RULE_ROW_CONTROL_STYLE_CLASS = "rule-row-control";
    private static final double ACTION_TYPE_COMBO_WIDTH = 190;

    @FXML
    private TextField ruleNameField;
    @FXML
    private ComboBox<String> conditionMatchModeCombo;
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
        ruleNameField.setId("ruleNameField");
        ruleNameField.setPromptText(I18n.get("dialog.createRule.name"));
        ruleNameField.textProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
        conditionMatchModeCombo.getItems().setAll(matchModeText(RuleMatchMode.ALL), matchModeText(RuleMatchMode.ANY));
        conditionMatchModeCombo.setValue(matchModeText(RuleMatchMode.ALL));
        conditionMatchModeCombo.valueProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
    }

    public void setOnChange(Runnable listener) {
        changeListener = Objects.requireNonNull(listener);
        notifyChanged();
    }

    public void setRule(String name, List<RuleCondition> conditions, List<RuleAction> actions) {
        setRule(name, RuleMatchMode.ALL, conditions, actions);
    }

    public void setRule(String name, RuleMatchMode matchMode,
                        List<RuleCondition> conditions, List<RuleAction> actions) {
        clearContents();
        ruleNameField.setText(name == null ? "" : name);
        conditionMatchModeCombo.setValue(matchModeText(matchMode == null ? RuleMatchMode.ALL : matchMode));
        conditions.forEach(this::addConditionRow);
        actions.stream().filter(action -> action.getActionType() != ActionType.SET_NOTE)
                .forEach(this::addActionRow);
        if (conditionRows.isEmpty()) addConditionRow(null);
        if (actionRows.isEmpty()) addActionRow(null);
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

        setRule(name, RuleMatchMode.ALL, conditions, actions);
        notifyChanged();
    }

    public void clear() {
        prepareNewRule();
    }

    private void clearContents() {
        ruleNameField.clear();
        conditionMatchModeCombo.setValue(matchModeText(RuleMatchMode.ALL));
        conditionsContainer.getChildren().clear();
        conditionRows.clear();
        actionsContainer.getChildren().clear();
        actionRows.clear();
        notifyChanged();
    }

    public void prepareNewRule() {
        clearContents();
        addConditionRow(null);
        addActionRow(null);
    }

    public String getRuleName() {
        return ruleNameField.getText().trim();
    }

    public RuleMatchMode getMatchMode() {
        return matchModeFromText(conditionMatchModeCombo.getValue());
    }

    public List<RuleCondition> getConditions() {
        List<RuleCondition> conditions = new ArrayList<>();
        for (ConditionRow row : conditionRows) {
            MatchField field = matchFieldFromText(row.fieldCombo.getValue());
            Operator operator = operatorFromText(row.operatorCombo.getValue());
            String value = row.valueField.getText().trim();
            if (field == null || operator == null) continue;

            RuleCondition condition = RuleCondition.builder()
                    .matchField(field)
                    .operator(operator)
                    .value(value.isEmpty() ? null : value)
                    .build();
            if (operator == Operator.AMOUNT_RANGE) {
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
            ActionType actionType = actionTypeFromText(row.typeCombo.getValue());
            String target = row.targetCombo.getValue();
            if (actionType == null) continue;

            RuleAction action = RuleAction.builder().actionType(actionType).build();
            if (actionType == ActionType.ASSIGN_CATEGORY && target != null) {
                categoryService.getAllCategories().stream()
                        .filter(category -> category.getName().equals(target))
                        .findFirst().ifPresent(category -> action.setCategoryId(category.getId()));
            } else if (actionType == ActionType.ADD_LABEL && target != null) {
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
                .filter(row -> actionTypeFromText(row.typeCombo.getValue()) == ActionType.ASSIGN_CATEGORY)
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
        List<MatchField> fields = new ArrayList<>(List.of(
                MatchField.PAYEE_NAME, MatchField.PAYEE_OR_PURPOSE,
                MatchField.PURPOSE, MatchField.AMOUNT, MatchField.ACCOUNT));
        if (existing != null && !fields.contains(existing.getMatchField())) fields.add(existing.getMatchField());
        row.fieldCombo.getItems().setAll(fields.stream().map(this::fieldText).toList());
        List<Operator> operators = new ArrayList<>(List.of(Operator.CONTAINS, Operator.CONTAINS_NOT,
                Operator.EQUALS, Operator.NOT_EQUALS, Operator.REGEX, Operator.NOT_REGEX));
        if (existing != null && !operators.contains(existing.getOperator())) operators.add(existing.getOperator());
        row.operatorCombo.getItems().setAll(operators.stream()
                .map(RuleEditorController::operatorText).toList());
        if (existing != null) {
            row.fieldCombo.setValue(fieldText(existing.getMatchField()));
            row.operatorCombo.setValue(operatorText(existing.getOperator()));
            row.valueField.setText(existing.getValue() == null ? "" : existing.getValue());
        }
        row.fieldCombo.valueProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
        row.operatorCombo.valueProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
        row.valueField.textProperty().addListener((observable, oldValue, newValue) -> notifyChanged());
        row.removeButton.setOnAction(event -> {
            if (conditionRows.size() == 1) {
                row.fieldCombo.setValue(null);
                row.operatorCombo.setValue(null);
                row.valueField.clear();
                notifyChanged();
                return;
            }
            conditionsContainer.getChildren().removeAll(row.grid, row.separator);
            conditionRows.remove(row);
            notifyChanged();
        });
        conditionRows.add(row);
        conditionsContainer.getChildren().addAll(row.grid, row.separator);
        notifyChanged();
    }

    private void addActionRow(RuleAction existing) {
        ActionRow row = new ActionRow();
        row.typeCombo.getItems().setAll(Arrays.stream(ActionType.values())
                .filter(actionType -> actionType != ActionType.SET_NOTE).map(this::actionText).toList());
        row.typeCombo.setOnAction(event -> {
            onActionTypeChanged(row);
            notifyChanged();
        });
        if (existing != null) {
            row.typeCombo.setValue(actionText(existing.getActionType()));
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
            if (actionRows.size() == 1) {
                row.typeCombo.setValue(null);
                onActionTypeChanged(row);
                notifyChanged();
                return;
            }
            actionsContainer.getChildren().removeAll(row.grid, row.separator);
            actionRows.remove(row);
            notifyChanged();
        });
        actionRows.add(row);
        actionsContainer.getChildren().addAll(row.grid, row.separator);
        notifyChanged();
    }

    private void onActionTypeChanged(ActionRow row) {
        ActionType type = actionTypeFromText(row.typeCombo.getValue());
        row.targetCombo.setValue(null);
        row.targetCombo.setCreateChoice(null, null);
        row.targetCombo.setDisable(type == null);
        if (type == null) {
            row.targetCombo.setChoices(List.of());
            return;
        }
        List<String> choices = switch (type) {
            case ASSIGN_CATEGORY -> {
                row.targetCombo.setCreateChoice("picker.category.create", name ->
                        categoryService.createCategory(name, null, null).getName());
                yield categoryService.getAllCategories().stream().map(Category::getName).toList();
            }
            case ADD_LABEL -> {
                row.targetCombo.setCreateChoice("picker.labels.create", name ->
                        labelService.createLabel(name, DEFAULT_LABEL_COLOR, null).getName());
                yield labelService.getAllLabels().stream().map(org.fintrax.model.Label::getName).toList();
            }
            case SET_NOTE -> List.of();
        };
        row.targetCombo.setChoices(choices);
    }

    private void notifyChanged() {
        changeListener.run();
    }

    private String fieldText(MatchField field) {
        return I18n.get("ruleEditor.field." + switch (field) {
            case PAYEE_NAME -> "payeeName";
            case PAYEE_OR_PURPOSE -> "payeeOrPurpose";
            case PURPOSE -> "purpose";
            case AMOUNT -> "amount";
            case ACCOUNT -> "account";
            case TRANSACTION_TYPE -> "transactionType";
        });
    }

    private MatchField matchFieldFromText(String text) {
        return Arrays.stream(MatchField.values()).filter(field -> fieldText(field).equals(text)).findFirst().orElse(null);
    }

    private static String operatorText(Operator operator) {
        return I18n.get("ruleEditor.operator." + switch (operator) {
            case CONTAINS -> "contains";
            case CONTAINS_NOT -> "containsNot";
            case EQUALS -> "is";
            case NOT_EQUALS -> "isNot";
            case REGEX -> "matchesRegex";
            case NOT_REGEX -> "doesNotMatchRegex";
            case AMOUNT_RANGE -> "amountRange";
        });
    }

    private static Operator operatorFromText(String text) {
        return Arrays.stream(Operator.values()).filter(operator -> operatorText(operator).equals(text))
                .findFirst().orElse(null);
    }

    private String actionText(ActionType actionType) {
        return I18n.get(actionType == ActionType.ASSIGN_CATEGORY
                ? "ruleEditor.action.assignCategory" : "ruleEditor.action.assignLabel");
    }

    private ActionType actionTypeFromText(String text) {
        return Arrays.stream(ActionType.values()).filter(actionType -> actionType != ActionType.SET_NOTE)
                .filter(actionType -> actionText(actionType).equals(text)).findFirst().orElse(null);
    }

    private String matchModeText(RuleMatchMode matchMode) {
        return I18n.get("ruleEditor.matchMode." + matchMode.name().toLowerCase());
    }

    private RuleMatchMode matchModeFromText(String text) {
        return Arrays.stream(RuleMatchMode.values()).filter(mode -> matchModeText(mode).equals(text))
                .findFirst().orElse(RuleMatchMode.ALL);
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
        private final GridPane grid = rowGrid(25, 27, 38, 10);
        private final ComboBox<String> fieldCombo = new ComboBox<>();
        private final ComboBox<String> operatorCombo = new ComboBox<>();
        private final TextField valueField = new TextField();
        private final Button removeButton = removeButton();
        private final Separator separator = new Separator();

        private ConditionRow() {
            grid.getStyleClass().add("rule-condition-row");
            fieldCombo.getStyleClass().add(RULE_ROW_CONTROL_STYLE_CLASS);
            operatorCombo.getStyleClass().add(RULE_ROW_CONTROL_STYLE_CLASS);
            valueField.getStyleClass().add(RULE_ROW_CONTROL_STYLE_CLASS);
            fieldCombo.setMaxWidth(Double.MAX_VALUE);
            operatorCombo.setMaxWidth(Double.MAX_VALUE);
            valueField.setMaxWidth(Double.MAX_VALUE);
            separator.getStyleClass().add("rule-row-separator");
            grid.add(fieldCombo, 0, 0);
            grid.add(operatorCombo, 1, 0);
            grid.add(valueField, 2, 0);
            grid.add(removeButton, 3, 0);
        }

        private boolean isValid() {
            Operator operator = operatorFromText(operatorCombo.getValue());
            if (fieldCombo.getValue() == null || operator == null || valueField.getText().isBlank()) return false;
            if (operator == Operator.REGEX || operator == Operator.NOT_REGEX) {
                try {
                    Pattern.compile(valueField.getText());
                } catch (PatternSyntaxException exception) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class ActionRow {
        private final GridPane grid = rowGrid(35, 25, 30, 10);
        private final ComboBox<String> typeCombo = new ComboBox<>();
        private final SearchableChoiceButton targetCombo = new SearchableChoiceButton();
        private final Button removeButton = removeButton();
        private final Separator separator = new Separator();

        private ActionRow() {
            grid.getStyleClass().add("rule-action-row");
            typeCombo.getStyleClass().add(RULE_ROW_CONTROL_STYLE_CLASS);
            typeCombo.setMinWidth(ACTION_TYPE_COMBO_WIDTH);
            typeCombo.setPrefWidth(ACTION_TYPE_COMBO_WIDTH);
            typeCombo.setMaxWidth(Double.MAX_VALUE);
            targetCombo.setMaxWidth(Double.MAX_VALUE);
            targetCombo.getStyleClass().add(RULE_ROW_CONTROL_STYLE_CLASS);
            targetCombo.setDisable(true);
            separator.getStyleClass().add("rule-row-separator");
            grid.add(typeCombo, 0, 0);
            grid.add(targetCombo, 1, 0, 2, 1);
            grid.add(removeButton, 3, 0);
        }

        private boolean isValid() {
            return typeCombo.getValue() != null && targetCombo.getValue() != null;
        }
    }

    private static GridPane rowGrid(double... widths) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        for (double width : widths) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(width);
            column.setHgrow(javafx.scene.layout.Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        return grid;
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
