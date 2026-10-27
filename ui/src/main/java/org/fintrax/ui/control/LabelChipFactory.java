package org.fintrax.ui.control;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import java.util.Collection;
import java.util.List;

/** Creates consistently styled chips for transaction labels. */
public final class LabelChipFactory {
    private static final Color DEFAULT_COLOR = Color.web("#1976d2");

    private LabelChipFactory() {
    }

    /** Creates a horizontal row of chips, with an ellipsis when available width is limited. */
    public static HBox createAll(Collection<org.fintrax.model.Label> labels) {
        List<Label> chips = labels.stream().map(LabelChipFactory::create).toList();
        return new LabelChipStrip(chips);
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

    private static final class LabelChipStrip extends HBox {
        private final List<Label> chips;
        private final Label overflow = new Label("…");

        private LabelChipStrip(List<Label> chips) {
            super(4);
            this.chips = chips;
            setAlignment(Pos.CENTER_LEFT);
            setMinWidth(0);
            setMaxWidth(Double.MAX_VALUE);
            getChildren().setAll(chips);
            overflow.setPadding(new Insets(2, 3, 2, 3));
            overflow.setMouseTransparent(true);
            overflow.getStyleClass().add("label-chip-overflow");
            overflow.setVisible(false);
            overflow.setManaged(false);
            getChildren().add(overflow);
        }

        @Override
        protected void layoutChildren() {
            updateVisibleChips();
            super.layoutChildren();
        }

        private void updateVisibleChips() {
            double contentWidth = Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset());
            double totalWidth = chips.stream().mapToDouble(chip -> chip.prefWidth(-1)).sum()
                    + Math.max(0, chips.size() - 1) * getSpacing();
            if (totalWidth <= contentWidth) {
                setVisibleChips(chips.size());
                overflow.setManaged(false);
                overflow.setVisible(false);
                return;
            }

            double budget = contentWidth - overflow.prefWidth(-1) - (chips.isEmpty() ? 0 : getSpacing());
            double usedWidth = 0;
            int visibleCount = 0;
            for (Label chip : chips) {
                double nextWidth = usedWidth + (visibleCount == 0 ? 0 : getSpacing()) + chip.prefWidth(-1);
                if (nextWidth > budget) break;
                usedWidth = nextWidth;
                visibleCount++;
            }
            setVisibleChips(visibleCount);
            overflow.setManaged(true);
            overflow.setVisible(true);
        }

        private void setVisibleChips(int visibleCount) {
            for (int index = 0; index < chips.size(); index++) {
                boolean visible = index < visibleCount;
                chips.get(index).setManaged(visible);
                chips.get(index).setVisible(visible);
            }
        }
    }
}
