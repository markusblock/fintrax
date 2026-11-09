package org.fintrax.ui.controller;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.stage.Window;
import org.fintrax.config.I18n;
import org.fintrax.model.RuleAction;
import org.fintrax.model.RuleCondition;
import org.fintrax.model.RuleMatchMode;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.ViewLoader;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class RuleEditorDialog {
    private RuleEditorDialog() {}

    static Optional<RuleInput> show(Window owner, ViewLoader viewLoader,
                                   String titleKey, String headerKey,
                                   String saveButtonId, String cancelButtonId,
                                   Consumer<RuleEditorController> initializeEditor) {
        Parent editorView;
        try {
            editorView = viewLoader.load("ruleEditor");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the rule editor", exception);
        }
        RuleEditorController editor = (RuleEditorController) editorView.getProperties()
                .get(RuleEditorController.class.getName());
        initializeEditor.accept(editor);

        Dialog<ButtonType> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.setResizable(true);
        dialog.initOwner(owner);
        dialog.setTitle(I18n.get(titleKey));
        dialog.setHeaderText(I18n.get(headerKey));

        ButtonType saveType = new ButtonType(I18n.get("button.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveType, ButtonType.CANCEL);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.setId(saveButtonId);
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setId(cancelButtonId);

        ScrollPane editorScrollPane = new ScrollPane(editorView);
        editorScrollPane.setFitToWidth(true);
        dialog.getDialogPane().setContent(editorScrollPane);

        editor.setOnChange(() -> saveButton.setDisable(!editor.isValid()));
        if (dialog.showAndWait().filter(saveType::equals).isEmpty()) return Optional.empty();
        return Optional.of(new RuleInput(editor.getRuleName(), editor.getMatchMode(),
                editor.getConditions(), editor.getActions()));
    }

    record RuleInput(String name, RuleMatchMode matchMode,
                     List<RuleCondition> conditions, List<RuleAction> actions) {}
}
