package org.fintrax.ui.controller;

import javafx.geometry.Rectangle2D;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fintrax.config.I18n;
import org.fintrax.ui.FintraxUI;

/** Content-sized PIN prompt shared by connection, import, and synchronization flows. */
final class PinPromptDialog {
    private static final double PREFERRED_WIDTH = 420;
    private static final double MINIMUM_WIDTH = 350;
    private static final double VIEWPORT_MARGIN = 48;

    private PinPromptDialog() {
    }

    static String show(Window owner, String bankName, int accountCount, String confirmLabel) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(I18n.get("dialog.pin.title"));
        String header = accountCount > 1
                ? I18n.get("dialog.pin.header.multiple", bankName, accountCount)
                : I18n.get("dialog.pin.header", bankName);
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) dialog.initOwner(owner);

        PasswordField pinField = new PasswordField();
        pinField.setPromptText(I18n.get("dialog.pin.prompt"));
        pinField.setPrefColumnCount(25);
        pinField.setMaxWidth(Double.MAX_VALUE);
        Label heading = new Label(header);
        heading.getStyleClass().add("dialog-title");
        Label pinLabel = new Label(I18n.get("dialog.pin.prompt"));
        pinLabel.setLabelFor(pinField);
        VBox content = new VBox(heading, pinLabel, pinField);
        content.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(content);

        ButtonType confirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        dialog.setResultConverter(button -> button == confirm ? pinField.getText() : null);
        dialog.setOnShown(event -> {
            pinField.requestFocus();
            sizeToViewport(dialog.getDialogPane(), owner);
        });
        dialog.setOnHidden(event -> pinField.clear());
        FintraxUI.configureDialog(dialog);
        return dialog.showAndWait().filter(pin -> !pin.isBlank()).orElse(null);
    }

    private static void sizeToViewport(javafx.scene.control.DialogPane pane, Window owner) {
        Screen screen = owner == null ? Screen.getPrimary()
                : Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                        .stream().findFirst().orElse(Screen.getPrimary());
        Rectangle2D bounds = screen.getVisualBounds();
        double maxWidth = Math.max(1, bounds.getWidth() - VIEWPORT_MARGIN);
        double width = Math.min(PREFERRED_WIDTH, maxWidth);
        pane.setMinWidth(Math.min(MINIMUM_WIDTH, width));
        pane.setPrefWidth(width);
        pane.setMaxWidth(maxWidth);
        pane.setMaxHeight(Math.max(1, bounds.getHeight() - VIEWPORT_MARGIN));
        if (pane.getScene().getWindow() instanceof Stage stage) {
            stage.sizeToScene();
        }
    }
}
