package org.fintrax.ui.controller;

import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Window;
import org.fintrax.config.I18n;
import org.fintrax.model.Rule;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.control.SearchableDropdownPicker;

import java.util.List;
import java.util.Optional;

final class RuleMergeDialog {
    private RuleMergeDialog() {}

    static Optional<MergeOptions> show(Window owner, List<Rule> sourceRules, List<Rule> existingRules) {
        Dialog<MergeOptions> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.initOwner(owner);
        dialog.setTitle(I18n.get("rules.merge.dialog.title"));
        dialog.setHeaderText(I18n.get("rules.merge.dialog.header", sourceRules.size()));
        setDialogWidth(dialog, owner);

        ButtonType cancelType = new ButtonType(I18n.get("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType continueType = new ButtonType(
                I18n.get("rules.merge.dialog.continue"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(cancelType, continueType);

        ToggleGroup destinationGroup = new ToggleGroup();
        RadioButton newRuleOption = new RadioButton(I18n.get("rules.merge.dialog.newRule"));
        newRuleOption.setId("mergeIntoNewRule");
        newRuleOption.setToggleGroup(destinationGroup);
        boolean allowNewRule = sourceRules.size() > 1;
        newRuleOption.setDisable(!allowNewRule);
        newRuleOption.setSelected(allowNewRule);
        Label newRuleDescription = new Label(I18n.get("rules.merge.dialog.newRule.description"));
        newRuleDescription.setWrapText(true);

        RadioButton existingRuleOption = new RadioButton(I18n.get("rules.merge.dialog.existingRule"));
        existingRuleOption.setId("mergeIntoExistingRule");
        existingRuleOption.setToggleGroup(destinationGroup);
        existingRuleOption.setDisable(existingRules.isEmpty());
        if (!allowNewRule) existingRuleOption.setSelected(true);
        Label existingRuleDescription = new Label(I18n.get("rules.merge.dialog.existingRule.description"));
        existingRuleDescription.setWrapText(true);

        String targetPrompt = I18n.get("rules.merge.dialog.target.prompt");
        Rule[] selectedTarget = {null};
        Button targetRuleButton = new Button(targetPrompt);
        targetRuleButton.setId("mergeTargetRuleButton");
        targetRuleButton.setMaxWidth(Double.MAX_VALUE);
        targetRuleButton.setMinHeight(36);
        targetRuleButton.getStyleClass().add("button-outlined");
        targetRuleButton.setDisable(true);

        CheckBox deleteSourceRules = new CheckBox(I18n.get("rules.merge.dialog.deleteSources"));
        deleteSourceRules.setId("deleteSourceRulesCheckBox");
        deleteSourceRules.setSelected(true);
        Label deleteHint = new Label(I18n.get("rules.merge.dialog.deleteSources.hint"));
        deleteHint.setWrapText(true);

        VBox newRuleChoice = new VBox(8, newRuleOption, newRuleDescription);
        VBox existingRuleChoice = new VBox(8,
                existingRuleOption, existingRuleDescription, targetRuleButton);
        VBox deleteSection = new VBox(8, new Separator(), deleteSourceRules, deleteHint);
        VBox content = new VBox(12, newRuleChoice, existingRuleChoice, deleteSection);
        content.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(content);

        Button continueButton = (Button) dialog.getDialogPane().lookupButton(continueType);
        continueButton.setId("mergeRulesContinue");
        dialog.getDialogPane().lookupButton(cancelType).setId("mergeRulesCancel");
        Runnable updateState = () -> {
            boolean choosingExisting = existingRuleOption.isSelected();
            targetRuleButton.setDisable(!choosingExisting);
            continueButton.setDisable(!allowNewRule && !choosingExisting
                    || choosingExisting && selectedTarget[0] == null);
        };
        existingRuleOption.selectedProperty().addListener((observable, oldValue, newValue) -> updateState.run());
        targetRuleButton.setOnAction(event -> SearchableDropdownPicker.showOne(
                targetRuleButton, existingRules, selectedTarget[0], RuleMergeDialog::ruleDisplayText,
                null, null, new SearchableDropdownPicker.SingleSelection<>("picker.selection.clear", target -> {
                    selectedTarget[0] = target;
                    targetRuleButton.setText(target == null ? targetPrompt : ruleDisplayText(target));
                    updateState.run();
                })));
        updateState.run();

        dialog.setResultConverter(button -> button == continueType
                ? new MergeOptions(newRuleOption.isSelected() || selectedTarget[0] == null
                        ? null : selectedTarget[0].getId(),
                        deleteSourceRules.isSelected())
                : null);
        dialog.setOnShown(event -> {
            if (existingRuleOption.isSelected()) targetRuleButton.requestFocus();
            else newRuleOption.requestFocus();
        });
        return dialog.showAndWait();
    }

    private static String ruleDisplayText(Rule rule) {
        return I18n.get("rules.ruleReference", rule.getName(), rule.getPriority());
    }

    private static void setDialogWidth(Dialog<?> dialog, Window owner) {
        Screen screen = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary());
        Rectangle2D bounds = screen.getVisualBounds();
        double width = Math.min(420, bounds.getWidth() - 48);
        dialog.getDialogPane().setMinWidth(Math.min(350, width));
        dialog.getDialogPane().setPrefWidth(width);
    }

    record MergeOptions(Long targetRuleId, boolean deleteSourceRules) {}
}
