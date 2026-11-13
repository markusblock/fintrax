package org.fintrax.ui.controller;

import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.stage.Screen;
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
import java.util.function.Predicate;

final class RuleEditorDialog {
    private static final double DIALOG_WIDTH = 900;
    private static final double DIALOG_HEIGHT = 560;
    private static final double SCREEN_MARGIN = 48;

    private RuleEditorDialog() {}

    static Optional<RuleInput> show(Window owner, ViewLoader viewLoader,
                                   String titleKey, String headerKey,
                                   String saveButtonId, String cancelButtonId,
                                   Consumer<RuleEditorController> initializeEditor,
                                   Predicate<String> ruleNameAvailable) {
        Parent editorView;
        try {
            editorView = viewLoader.load("ruleEditor");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the rule editor", exception);
        }
        RuleEditorController editor = (RuleEditorController) editorView.getProperties()
                .get(RuleEditorController.class.getName());
        editorView.getStyleClass().add("dialog-content");
        initializeEditor.accept(editor);
        editor.setRuleNameAvailability(ruleNameAvailable);

        Dialog<ButtonType> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.setResizable(true);
        dialog.initOwner(owner);
        dialog.setTitle(I18n.get(titleKey));
        dialog.setHeaderText(I18n.get(headerKey));

        ButtonType saveType = new ButtonType(I18n.get("button.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(I18n.get("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(saveType, cancelType);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.setId(saveButtonId);
        dialog.getDialogPane().lookupButton(cancelType).setId(cancelButtonId);

        ScrollPane editorScrollPane = new ScrollPane(editorView);
        editorScrollPane.setFitToWidth(true);
        dialog.getDialogPane().setContent(editorScrollPane);
        dialog.setOnShown(event -> resizeDialog(dialog, owner));

        editor.setOnChange(() -> saveButton.setDisable(!editor.isValid()));
        if (dialog.showAndWait().filter(saveType::equals).isEmpty()) return Optional.empty();
        return Optional.of(new RuleInput(editor.getRuleName(), editor.getMatchMode(),
                editor.getConditions(), editor.getActions()));
    }

    private static void resizeDialog(Dialog<?> dialog, Window owner) {
        Screen screen = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary());
        Rectangle2D visualBounds = screen.getVisualBounds();
        double width = Math.min(DIALOG_WIDTH, visualBounds.getWidth() - SCREEN_MARGIN);
        dialog.getDialogPane().setMinWidth(width);
        dialog.getDialogPane().setPrefWidth(width);
        Window dialogWindow = dialog.getDialogPane().getScene().getWindow();
        dialogWindow.setWidth(width);
        dialogWindow.setHeight(Math.min(DIALOG_HEIGHT, visualBounds.getHeight() - SCREEN_MARGIN));
    }

    record RuleInput(String name, RuleMatchMode matchMode,
                     List<RuleCondition> conditions, List<RuleAction> actions) {}
}
