package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
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
import org.fintrax.ui.control.CategoryHierarchy;
import org.fintrax.ui.control.SearchableDropdownPicker;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
@Scope("prototype")
public class RuleEditorController {
    private static final String DEFAULT_LABEL_COLOR = "#1976d2";
    private static final String RULE_ROW_CONTROL_STYLE_CLASS = "rule-row-control";
    private static final double CONDITION_OPERATOR_COMBO_WIDTH = 180;
    private static final double COMBO_BOX_ARROW_AND_PADDING_WIDTH = 48;

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
            RuleTargetChoice target = row.selectedTarget;
            if (actionType == null) continue;

            RuleAction action = RuleAction.builder().actionType(actionType).build();
            if (actionType == ActionType.ASSIGN_CATEGORY && target != null) {
                action.setCategoryId(target.id());
            } else if (actionType == ActionType.ADD_LABEL && target != null) {
                action.setLabelId(target.id());
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
        fitComboBoxToItems(row.fieldCombo);
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
        fitComboBoxToItems(row.typeCombo);
        row.typeCombo.setOnAction(event -> {
            onActionTypeChanged(row);
            notifyChanged();
        });
        if (existing != null) {
            row.typeCombo.setValue(actionText(existing.getActionType()));
            onActionTypeChanged(row);
            if (existing.getCategoryId() != null) {
                row.targetOptions.stream().filter(option -> existing.getCategoryId().equals(option.id()))
                        .findFirst().ifPresent(option -> setTargetChoice(row, option));
            }
            if (existing.getLabelId() != null) {
                row.targetOptions.stream().filter(option -> existing.getLabelId().equals(option.id()))
                        .findFirst().ifPresent(option -> setTargetChoice(row, option));
            }
        }
        row.targetButton.setOnAction(event -> showTargetPicker(row));
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
        row.targetType = type;
        row.selectedTarget = null;
        row.targetButton.setText("");
        row.targetButton.setDisable(type == null);
        if (type == null) {
            row.targetOptions = List.of();
            row.createTarget = null;
            return;
        }
        switch (type) {
            case ASSIGN_CATEGORY -> {
                List<Category> categories = categoryService.getAllCategories();
                Function<Category, String> displayPath = CategoryHierarchy.displayPaths(categories);
                row.targetOptions = categories.stream()
                        .map(category -> new RuleTargetChoice(category.getId(), displayPath.apply(category)))
                        .toList();
                row.createTextKey = "picker.category.create";
                row.createTarget = name -> {
                    Category created = categoryService.createCategory(name, null, null);
                    return addTargetOption(row, created.getId(), displayPath.apply(created));
                };
            }
            case ADD_LABEL -> {
                row.targetOptions = labelService.getAllLabels().stream()
                        .map(label -> new RuleTargetChoice(label.getId(), label.getName()))
                        .toList();
                row.createTextKey = "picker.labels.create";
                row.createTarget = name -> {
                    org.fintrax.model.Label created = labelService.createLabel(name, DEFAULT_LABEL_COLOR, null);
                    return addTargetOption(row, created.getId(), created.getName());
                };
            }
            case SET_NOTE -> {
                row.targetOptions = List.of();
                row.createTarget = null;
            }
        }
        row.targetButton.setAccessibleText(I18n.get(type == ActionType.ASSIGN_CATEGORY
                ? "picker.category.accessible" : "picker.labels.accessible"));
    }

    private void showTargetPicker(ActionRow row) {
        if (row.targetType == null) return;
        SearchableDropdownPicker.showOne(row.targetButton, row.targetOptions, row.selectedTarget,
                RuleTargetChoice::displayText, row.createTextKey, row.createTarget,
                "picker.selection.clear",
                selected -> setTargetChoice(row, selected));
    }

    private void setTargetChoice(ActionRow row, RuleTargetChoice choice) {
        row.selectedTarget = choice;
        row.targetButton.setText(choice == null ? "" : choice.displayText());
        notifyChanged();
    }

    private RuleTargetChoice addTargetOption(ActionRow row, Long id, String displayText) {
        RuleTargetChoice choice = new RuleTargetChoice(id, displayText);
        List<RuleTargetChoice> options = new ArrayList<>(row.targetOptions);
        options.add(choice);
        row.targetOptions = List.copyOf(options);
        return choice;
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
        private final GridPane grid = conditionRowGrid();
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
            valueField.setPrefColumnCount(25);
            valueField.setMinWidth(Region.USE_PREF_SIZE);
            fieldCombo.setMaxWidth(Double.MAX_VALUE);
            operatorCombo.setMinWidth(CONDITION_OPERATOR_COMBO_WIDTH);
            operatorCombo.setMaxWidth(Double.MAX_VALUE);
            valueField.setMaxWidth(Double.MAX_VALUE);
            separator.getStyleClass().add("rule-row-separator");
            grid.add(fieldCombo, 0, 0);
            grid.add(operatorCombo, 1, 0);
            grid.add(valueField, 2, 0);
            grid.add(removeButton, 3, 0);
        }

        private static GridPane conditionRowGrid() {
            GridPane grid = new GridPane();
            grid.setHgap(8);
            ColumnConstraints fieldColumn = new ColumnConstraints();
            fieldColumn.setHgrow(Priority.NEVER);
            grid.getColumnConstraints().add(fieldColumn);
            ColumnConstraints operatorColumn = new ColumnConstraints();
            operatorColumn.setMinWidth(CONDITION_OPERATOR_COMBO_WIDTH);
            operatorColumn.setPrefWidth(CONDITION_OPERATOR_COMBO_WIDTH);
            operatorColumn.setHgrow(Priority.NEVER);
            grid.getColumnConstraints().add(operatorColumn);
            ColumnConstraints valueColumn = new ColumnConstraints();
            valueColumn.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(valueColumn);
            ColumnConstraints removeColumn = new ColumnConstraints();
            removeColumn.setHalignment(HPos.RIGHT);
            removeColumn.setHgrow(Priority.NEVER);
            grid.getColumnConstraints().add(removeColumn);
            grid.setMaxWidth(Double.MAX_VALUE);
            return grid;
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
        private final GridPane grid = actionRowGrid();
        private final ComboBox<String> typeCombo = new ComboBox<>();
        private final Button targetButton = new Button();
        private final Button removeButton = removeButton();
        private final Separator separator = new Separator();
        private List<RuleTargetChoice> targetOptions = List.of();
        private RuleTargetChoice selectedTarget;
        private ActionType targetType;
        private String createTextKey;
        private Function<String, RuleTargetChoice> createTarget;

        private ActionRow() {
            grid.getStyleClass().add("rule-action-row");
            typeCombo.getStyleClass().add(RULE_ROW_CONTROL_STYLE_CLASS);
            typeCombo.setMaxWidth(Double.MAX_VALUE);
            targetButton.setMaxWidth(Double.MAX_VALUE);
            targetButton.getStyleClass().addAll(RULE_ROW_CONTROL_STYLE_CLASS, "rule-target-picker-button");
            targetButton.setDisable(true);
            Region dropdownArrow = new Region();
            dropdownArrow.getStyleClass().add("rule-target-picker-arrow");
            dropdownArrow.setMinSize(8, 5);
            dropdownArrow.setPrefSize(8, 5);
            dropdownArrow.setMaxSize(8, 5);
            targetButton.setGraphic(dropdownArrow);
            targetButton.setContentDisplay(ContentDisplay.RIGHT);
            separator.getStyleClass().add("rule-row-separator");
            grid.add(typeCombo, 0, 0);
            grid.add(targetButton, 1, 0, 2, 1);
            grid.add(removeButton, 3, 0);
        }

        private boolean isValid() {
            return typeCombo.getValue() != null && selectedTarget != null;
        }
    }

    private record RuleTargetChoice(Long id, String displayText) {
    }

    private static GridPane actionRowGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        ColumnConstraints typeColumn = new ColumnConstraints();
        typeColumn.setHgrow(Priority.NEVER);
        grid.getColumnConstraints().add(typeColumn);
        ColumnConstraints firstTargetColumn = new ColumnConstraints();
        firstTargetColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(firstTargetColumn);
        ColumnConstraints secondTargetColumn = new ColumnConstraints();
        secondTargetColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(secondTargetColumn);
        ColumnConstraints removeColumn = new ColumnConstraints();
        removeColumn.setHalignment(HPos.RIGHT);
        removeColumn.setHgrow(Priority.NEVER);
        grid.getColumnConstraints().add(removeColumn);
        grid.setMaxWidth(Double.MAX_VALUE);
        return grid;
    }

    private static void fitComboBoxToItems(ComboBox<String> combo) {
        double widestLabel = combo.getItems().stream()
                .mapToDouble(label -> {
                    Text text = new Text(label);
                    return text.getLayoutBounds().getWidth();
                })
                .max()
                .orElse(0);
        double horizontalInsets = combo.getInsets().getLeft() + combo.getInsets().getRight();
        double width = Math.ceil(widestLabel + horizontalInsets + COMBO_BOX_ARROW_AND_PADDING_WIDTH);
        combo.setMinWidth(width);
        combo.setPrefWidth(width);
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
