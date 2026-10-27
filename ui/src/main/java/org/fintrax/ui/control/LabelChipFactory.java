package org.fintrax.ui.control;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;

import java.util.Collection;

/** Creates consistently styled chips for transaction labels. */
public final class LabelChipFactory {
    private static final Color DEFAULT_COLOR = Color.web("#1976d2");

    private LabelChipFactory() {
    }

    /** Creates a wrapping group of chips for the supplied labels. */
    public static FlowPane createAll(Collection<org.fintrax.model.Label> labels) {
        FlowPane chips = new FlowPane(4, 2);
        chips.setAlignment(Pos.CENTER_LEFT);
        labels.stream().map(LabelChipFactory::create).forEach(chips.getChildren()::add);
        return chips;
    }

    /** Creates one chip using the label's configured color. */
    public static Label create(org.fintrax.model.Label label) {
        Color color = parseColor(label.getColor());
        Label chip = new Label(label.getName());
        chip.getStyleClass().add("label-chip");
        chip.setMouseTransparent(true);
        chip.setPadding(new Insets(2, 7, 2, 7));
        chip.setTextFill(contrastingTextColor(color));
        chip.setBackground(new Background(new BackgroundFill(
                color, new CornerRadii(9), Insets.EMPTY)));
        return chip;
    }

    private static Color parseColor(String value) {
        if (value == null || value.isBlank()) return DEFAULT_COLOR;
        try {
            return Color.web(value);
        } catch (IllegalArgumentException exception) {
            return Color.BLACK;
        }
    }

    private static Color contrastingTextColor(Color background) {
        double luminance = 0.2126 * linearize(background.getRed())
                + 0.7152 * linearize(background.getGreen())
                + 0.0722 * linearize(background.getBlue());
        return luminance > 0.179 ? Color.BLACK : Color.WHITE;
    }

    private static double linearize(double colorChannel) {
        return colorChannel <= 0.04045
                ? colorChannel / 12.92
                : Math.pow((colorChannel + 0.055) / 1.055, 2.4);
    }
}
