package org.fintrax.ui.control;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.event.Event;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.List;

/**
 * Shows a tooltip with the cell's full text while the mouse hovers a truncated
 * table cell. Cells clip their content with ellipsis, so the tooltip makes long
 * values readable.
 *
 * <p>The tooltip is installed only after the pointer dwelled on the cell and only
 * for truncated content. A popup therefore never exists before or during a click
 * on a cell, which keeps clicks on table cells reliable.</p>
 */
public final class TableCellTooltips {
    private static final Text TEXT_MEASURER = new Text();
    private static final Duration DWELL_TIME = Duration.millis(400);
    private static final String DWELL_KEY = "fintrax.tableCellTooltipDwell";

    private TableCellTooltips() {}

    /** Wraps the cell factory of every current column of the table. */
    public static <S> void install(TableView<S> table) {
        for (TableColumn<S, ?> column : List.copyOf(table.getColumns())) {
            installOn(column);
        }
    }

    private static <S, T> void installOn(TableColumn<S, T> column) {
        Callback<TableColumn<S, T>, TableCell<S, T>> previous = column.getCellFactory();
        column.setCellFactory(col -> {
            TableCell<S, T> cell = previous == null ? defaultCell() : previous.call(col);
            cell.addEventFilter(MouseEvent.MOUSE_ENTERED, event -> startDwell(cell));
            cell.addEventFilter(MouseEvent.MOUSE_EXITED, event -> clearTooltip(cell));
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> clearTooltip(cell));
            return cell;
        });
    }

    private static <S, T> TableCell<S, T> defaultCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.toString());
                    setGraphic(null);
                }
            }
        };
    }

    private static void startDwell(TableCell<?, ?> cell) {
        PauseTransition dwell = new PauseTransition(DWELL_TIME);
        dwell.setOnFinished(event -> refresh(cell));
        dwell.play();
        cell.getProperties().put(DWELL_KEY, dwell);
    }

    private static void clearTooltip(TableCell<?, ?> cell) {
        Object dwell = cell.getProperties().remove(DWELL_KEY);
        if (dwell instanceof PauseTransition pending) {
            pending.stop();
        }
        Tooltip tooltip = cell.getTooltip();
        if (tooltip != null) {
            tooltip.hide();
            cell.setTooltip(null);
        }
    }

    private static void refresh(TableCell<?, ?> cell) {
        cell.getProperties().remove(DWELL_KEY);
        String text = cell.getText();
        boolean truncated;
        if (text != null && !text.isBlank()) {
            truncated = isTextTruncated(cell, text);
        } else if (cell.getItem() != null) {
            text = cell.getItem().toString();
            truncated = LabelChipFactory.isTruncated(cell.getGraphic());
        } else {
            truncated = false;
        }
        if (!truncated || text == null || text.isBlank()) {
            cell.setTooltip(null);
            return;
        }
        Tooltip tooltip = cell.getTooltip();
        if (tooltip == null) {
            tooltip = new Tooltip(text);
            // Let a click that dismisses the visible tooltip still reach the cell.
            tooltip.setConsumeAutoHidingEvents(false);
            cell.setTooltip(tooltip);
        } else {
            tooltip.setText(text);
        }
        arm(cell);
    }

    private static void arm(TableCell<?, ?> cell) {
        // JavaFX arms a tooltip only on MOUSE_MOVED; fire one so a hover that
        // stays still (wheel scroll, freeze) still shows the tooltip.
        double x = cell.getWidth() / 2;
        double y = cell.getHeight() / 2;
        Point2D screen = cell.localToScreen(x, y);
        if (screen == null) return;
        Event.fireEvent(cell, new MouseEvent(MouseEvent.MOUSE_MOVED, x, y,
                screen.getX(), screen.getY(), null, 0,
                false, false, false, false, false, false, false, false, false, false, null));
    }

    private static boolean isTextTruncated(TableCell<?, ?> cell, String text) {
        TEXT_MEASURER.setText(text);
        TEXT_MEASURER.setFont(cell.getFont());
        double textWidth = TEXT_MEASURER.getLayoutBounds().getWidth();
        Insets padding = cell.getPadding();
        Insets borderInsets = cell.getBorder() == null ? Insets.EMPTY : cell.getBorder().getInsets();
        double availableWidth = cell.getWidth() - padding.getLeft() - padding.getRight()
                - Math.max(0, borderInsets.getLeft()) - Math.max(0, borderInsets.getRight());
        return availableWidth > 0 && textWidth > availableWidth;
    }
}