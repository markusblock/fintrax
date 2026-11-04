package org.fintrax.ui.controller;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.stage.Window;
import org.fintrax.config.I18n;
import org.fintrax.model.Transaction;
import org.fintrax.service.RuleService;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.ViewLoader;

import java.io.IOException;

final class CreateRuleFromTransactionDialog {
    private CreateRuleFromTransactionDialog() {}

    static void show(Window owner, Transaction transaction, RuleService ruleService, ViewLoader viewLoader) {
        Parent editorView;
        try {
            editorView = viewLoader.load("ruleEditor");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the rule editor", exception);
        }
        RuleEditorController editor = (RuleEditorController) editorView.getProperties()
                .get(RuleEditorController.class.getName());
        editor.setFromTransaction(transaction);

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

        ScrollPane editorScrollPane = new ScrollPane(editorView);
        editorScrollPane.setFitToWidth(true);
        editorScrollPane.setPrefViewportHeight(560);
        dialog.getDialogPane().setContent(editorScrollPane);

        editor.setOnChange(() -> saveButton.setDisable(!editor.isValid()));
        if (dialog.showAndWait().filter(saveType::equals).isEmpty()) return;

        ruleService.createRule(editor.getRuleName(), editor.getConditions(), editor.getActions());
    }
}
