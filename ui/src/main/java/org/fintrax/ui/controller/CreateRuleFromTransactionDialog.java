package org.fintrax.ui.controller;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
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
import org.fintrax.service.RuleService;
import org.fintrax.ui.FintraxUI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CreateRuleFromTransactionDialog {
    private CreateRuleFromTransactionDialog() {}

    static void show(Window owner, Transaction transaction, RuleService ruleService,
                     CategoryService categoryService, LabelService labelService) {
        String payeeName = transaction.getPayeeDisplay() != null
                ? transaction.getPayeeDisplay() : transaction.getOriginalPayee();
        String purpose = transaction.getPurpose();

        Dialog<ButtonType> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.initOwner(owner);
        dialog.setTitle(I18n.get("dialog.createRule.title"));
        dialog.setHeaderText(I18n.get("dialog.createRule.header"));

        ButtonType saveType = new ButtonType(I18n.get("button.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveType, ButtonType.CANCEL);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.setId("createRuleSave");
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setId("createRuleCancel");

        TextField nameField = new TextField(defaultRuleName(payeeName));
        nameField.setId("createRuleName");
        nameField.setPromptText(I18n.get("dialog.createRule.name"));

        CheckBox payeeCondition = condition(
                "payeeNameCondition", "dialog.createRule.payeeCondition",
                "dialog.createRule.payeeUnavailable", payeeName);
        CheckBox purposeCondition = condition(
                "purposeCondition", "dialog.createRule.purposeCondition",
                "dialog.createRule.purposeUnavailable", purpose);

        Set<Long> transactionLabelIds = transaction.getLabelIds() == null
                ? Set.of() : transaction.getLabelIds();
        Category inheritedCategory = categoryService.getCategory(transaction.getCategoryId()).orElse(null);
        List<org.fintrax.model.Label> inheritedLabels = labelService.getAllLabels().stream()
                .filter(label -> transactionLabelIds.contains(label.getId()))
                .toList();
        boolean hasInheritedActions = inheritedCategory != null || !inheritedLabels.isEmpty();

        ComboBox<Category> categoryChoice = null;
        Map<CheckBox, org.fintrax.model.Label> labelChoices = new LinkedHashMap<>();
        VBox actionOptions = new VBox(4);
        if (hasInheritedActions) {
            if (inheritedCategory != null) {
                actionOptions.getChildren().add(new Label(I18n.get(
                        "dialog.createRule.inheritedCategory", inheritedCategory.getName())));
            }
            if (!inheritedLabels.isEmpty()) {
                String names = String.join(", ", inheritedLabels.stream()
                        .map(org.fintrax.model.Label::getName).toList());
                actionOptions.getChildren().add(new Label(I18n.get("dialog.createRule.inheritedLabels", names)));
            }
        } else {
            categoryChoice = new ComboBox<>();
            categoryChoice.setId("ruleCategoryAction");
            categoryChoice.getItems().setAll(categoryService.getAllCategories());
            categoryChoice.setPromptText(I18n.get("dialog.createRule.noCategory"));
            categoryChoice.setCellFactory(list -> categoryCell());
            categoryChoice.setButtonCell(categoryCell());
            actionOptions.getChildren().add(categoryChoice);

            labelService.getAllLabels().forEach(label -> {
                CheckBox choice = new CheckBox(label.getName());
                choice.setId("ruleLabelAction-" + label.getId());
                labelChoices.put(choice, label);
            });
            actionOptions.getChildren().addAll(labelChoices.keySet());
        }

        VBox content = new VBox(10,
                new Label(I18n.get("dialog.createRule.name")), nameField,
                new Label(I18n.get("dialog.createRule.conditions")), payeeCondition, purposeCondition,
                new Label(I18n.get("dialog.createRule.actions")), actionOptions);
        content.setPadding(new Insets(8, 0, 0, 0));
        content.setPrefWidth(430);
        dialog.getDialogPane().setContent(content);

        ComboBox<Category> selectableCategoryChoice = categoryChoice;
        Runnable updateSaveState = () -> {
            boolean hasCondition = payeeCondition.isSelected() || purposeCondition.isSelected();
            boolean hasAction = hasInheritedActions
                    || selectableCategoryChoice != null && selectableCategoryChoice.getValue() != null
                    || labelChoices.keySet().stream().anyMatch(CheckBox::isSelected);
            saveButton.setDisable(nameField.getText().isBlank() || !hasCondition || !hasAction);
        };
        nameField.textProperty().addListener((observable, oldValue, newValue) -> updateSaveState.run());
        payeeCondition.selectedProperty().addListener((observable, oldValue, newValue) -> updateSaveState.run());
        purposeCondition.selectedProperty().addListener((observable, oldValue, newValue) -> updateSaveState.run());
        if (selectableCategoryChoice != null) {
            selectableCategoryChoice.valueProperty()
                    .addListener((observable, oldValue, newValue) -> updateSaveState.run());
        }
        labelChoices.keySet().forEach(choice ->
                choice.selectedProperty().addListener((observable, oldValue, newValue) -> updateSaveState.run()));
        updateSaveState.run();

        if (dialog.showAndWait().filter(saveType::equals).isEmpty()) return;

        List<RuleCondition> conditions = new ArrayList<>();
        if (payeeCondition.isSelected()) {
            conditions.add(createCondition(MatchField.PAYEE_NAME, payeeName));
        }
        if (purposeCondition.isSelected()) {
            conditions.add(createCondition(MatchField.PURPOSE, purpose));
        }

        List<RuleAction> actions = new ArrayList<>();
        if (inheritedCategory != null) {
            actions.add(RuleAction.builder()
                    .actionType(ActionType.ASSIGN_CATEGORY)
                    .categoryId(inheritedCategory.getId())
                    .build());
        }
        inheritedLabels.forEach(label -> actions.add(RuleAction.builder()
                .actionType(ActionType.ADD_LABEL).labelId(label.getId()).build()));
        if (!hasInheritedActions) {
            Category selectedCategory = selectableCategoryChoice == null
                    ? null : selectableCategoryChoice.getValue();
            if (selectedCategory != null) {
                actions.add(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(selectedCategory.getId()).build());
            }
            labelChoices.forEach((choice, label) -> {
                if (choice.isSelected()) {
                    actions.add(RuleAction.builder().actionType(ActionType.ADD_LABEL)
                            .labelId(label.getId()).build());
                }
            });
        }

        ruleService.createRule(nameField.getText().trim(), conditions, actions);
    }

    private static CheckBox condition(String id, String textKey, String unavailableKey, String value) {
        boolean available = value != null && !value.isBlank();
        CheckBox condition = new CheckBox(available
                ? I18n.get(textKey, value)
                : I18n.get(unavailableKey));
        condition.setId(id);
        condition.setSelected(available);
        condition.setDisable(!available);
        condition.setWrapText(true);
        return condition;
    }

    private static String defaultRuleName(String payeeName) {
        return payeeName == null || payeeName.isBlank()
                ? I18n.get("dialog.createRule.defaultName")
                : I18n.get("dialog.createRule.defaultNameForPayee", payeeName);
    }

    private static RuleCondition createCondition(MatchField field, String value) {
        return RuleCondition.builder()
                .matchField(field)
                .operator(Operator.CONTAINS)
                .value(value)
                .build();
    }

    private static ListCell<Category> categoryCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Category category, boolean empty) {
                super.updateItem(category, empty);
                setText(empty || category == null ? null : category.getName());
            }
        };
    }
}
