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
        String payeeName = payeeName(transaction);
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

        ActionOptions actionOptions = createActionOptions(transaction, categoryService, labelService);

        VBox content = new VBox(10,
                new Label(I18n.get("dialog.createRule.name")), nameField,
                new Label(I18n.get("dialog.createRule.conditions")), payeeCondition, purposeCondition,
                new Label(I18n.get("dialog.createRule.actions")), actionOptions.content());
        content.setPadding(new Insets(8, 0, 0, 0));
        content.setPrefWidth(430);
        dialog.getDialogPane().setContent(content);

        installValidation(nameField, payeeCondition, purposeCondition, actionOptions, saveButton);

        if (dialog.showAndWait().filter(saveType::equals).isEmpty()) return;

        ruleService.createRule(nameField.getText().trim(),
                selectedConditions(payeeCondition, purposeCondition, payeeName, purpose),
                actionOptions.toRuleActions());
    }

    private static ActionOptions createActionOptions(Transaction transaction,
                                                     CategoryService categoryService,
                                                     LabelService labelService) {
        Set<Long> transactionLabelIds = transaction.getLabelIds() == null
                ? Set.of() : transaction.getLabelIds();
        Category inheritedCategory = categoryService.getCategory(transaction.getCategoryId()).orElse(null);
        List<org.fintrax.model.Label> inheritedLabels = labelService.getAllLabels().stream()
                .filter(label -> transactionLabelIds.contains(label.getId()))
                .toList();
        VBox content = new VBox(4);
        if (inheritedCategory != null || !inheritedLabels.isEmpty()) {
            addInheritedActionDescriptions(content, inheritedCategory, inheritedLabels);
            return new ActionOptions(content, inheritedCategory, inheritedLabels, null, Map.of());
        }

        ComboBox<Category> categoryChoice = new ComboBox<>();
        categoryChoice.setId("ruleCategoryAction");
        categoryChoice.getItems().setAll(categoryService.getAllCategories());
        categoryChoice.setPromptText(I18n.get("dialog.createRule.noCategory"));
        categoryChoice.setCellFactory(list -> categoryCell());
        categoryChoice.setButtonCell(categoryCell());
        content.getChildren().add(categoryChoice);

        Map<CheckBox, org.fintrax.model.Label> labelChoices = new LinkedHashMap<>();
        labelService.getAllLabels().forEach(label -> {
            CheckBox choice = new CheckBox(label.getName());
            choice.setId("ruleLabelAction-" + label.getId());
            labelChoices.put(choice, label);
        });
        content.getChildren().addAll(labelChoices.keySet());
        return new ActionOptions(content, null, List.of(), categoryChoice, labelChoices);
    }

    private static void addInheritedActionDescriptions(VBox content, Category category,
                                                       List<org.fintrax.model.Label> labels) {
        if (category != null) {
            content.getChildren().add(new Label(I18n.get("dialog.createRule.inheritedCategory", category.getName())));
        }
        if (!labels.isEmpty()) {
            String names = String.join(", ", labels.stream().map(org.fintrax.model.Label::getName).toList());
            content.getChildren().add(new Label(I18n.get("dialog.createRule.inheritedLabels", names)));
        }
    }

    private static void installValidation(TextField nameField, CheckBox payeeCondition, CheckBox purposeCondition,
                                          ActionOptions actionOptions, Button saveButton) {
        Runnable updateSaveState = () -> saveButton.setDisable(
                nameField.getText().isBlank()
                        || !payeeCondition.isSelected() && !purposeCondition.isSelected()
                        || !actionOptions.hasAction());
        nameField.textProperty().addListener((observable, oldValue, newValue) -> updateSaveState.run());
        payeeCondition.selectedProperty().addListener((observable, oldValue, newValue) -> updateSaveState.run());
        purposeCondition.selectedProperty().addListener((observable, oldValue, newValue) -> updateSaveState.run());
        actionOptions.addSelectionListeners(updateSaveState);
        updateSaveState.run();
    }

    private static List<RuleCondition> selectedConditions(CheckBox payeeCondition, CheckBox purposeCondition,
                                                          String payeeName, String purpose) {
        List<RuleCondition> conditions = new ArrayList<>();
        if (payeeCondition.isSelected()) conditions.add(createCondition(MatchField.PAYEE_NAME, payeeName));
        if (purposeCondition.isSelected()) conditions.add(createCondition(MatchField.PURPOSE, purpose));
        return conditions;
    }

    private static String payeeName(Transaction transaction) {
        return transaction.getPayeeDisplay() != null
                ? transaction.getPayeeDisplay() : transaction.getOriginalPayee();
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

    private record ActionOptions(VBox content, Category inheritedCategory,
                                 List<org.fintrax.model.Label> inheritedLabels,
                                 ComboBox<Category> categoryChoice,
                                 Map<CheckBox, org.fintrax.model.Label> labelChoices) {
        boolean hasAction() {
            return inheritedCategory != null || !inheritedLabels.isEmpty()
                    || categoryChoice != null && categoryChoice.getValue() != null
                    || labelChoices.keySet().stream().anyMatch(CheckBox::isSelected);
        }

        void addSelectionListeners(Runnable listener) {
            if (categoryChoice != null) {
                categoryChoice.valueProperty().addListener((observable, oldValue, newValue) -> listener.run());
            }
            labelChoices.keySet().forEach(choice ->
                    choice.selectedProperty().addListener((observable, oldValue, newValue) -> listener.run()));
        }

        List<RuleAction> toRuleActions() {
            List<RuleAction> actions = new ArrayList<>();
            if (inheritedCategory != null) {
                actions.add(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(inheritedCategory.getId()).build());
            }
            inheritedLabels.forEach(label -> actions.add(RuleAction.builder()
                    .actionType(ActionType.ADD_LABEL).labelId(label.getId()).build()));
            if (inheritedCategory == null && inheritedLabels.isEmpty()) {
                if (categoryChoice != null && categoryChoice.getValue() != null) {
                    actions.add(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                            .categoryId(categoryChoice.getValue().getId()).build());
                }
                labelChoices.forEach((choice, label) -> {
                    if (choice.isSelected()) {
                        actions.add(RuleAction.builder().actionType(ActionType.ADD_LABEL)
                                .labelId(label.getId()).build());
                    }
                });
            }
            return actions;
        }
    }
}
