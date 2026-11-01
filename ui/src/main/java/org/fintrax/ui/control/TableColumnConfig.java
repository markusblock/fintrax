package org.fintrax.ui.control;

import javafx.scene.control.TableColumn;

public record TableColumnConfig(String key, TableColumn<?, ?> column) {}
