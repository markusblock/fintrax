package org.fintrax.ui.control;

import javafx.scene.control.TableView;

/** Installs constrained column sizing without suppressing TableView's horizontal scrollbar. */
public final class TableViewResizePolicy {
    private TableViewResizePolicy() {
    }

    public static <S> void fitColumnsAndAllowHorizontalScrolling(TableView<S> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN::call);
    }
}
