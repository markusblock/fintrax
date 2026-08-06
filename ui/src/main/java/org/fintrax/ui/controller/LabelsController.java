package org.fintrax.ui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.Label;
import org.fintrax.service.LabelService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@Scope("prototype")
public class LabelsController {
    @FXML
    private BorderPane rootPane;
    @FXML
    private TableView<Label> labelTable;
    @FXML
    private TableColumn<Label, String> nameColumn;
    @FXML
    private TableColumn<Label, String> colorColumn;
    @FXML
    private TableColumn<Label, String> commentColumn;
    @FXML
    private TextField nameField;
    @FXML
    private ColorPicker colorPicker;
    @FXML
    private TextField commentField;
    @FXML
    private Button saveButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button addButton;

    private final LabelService labelService;
    private final ObservableList<Label> tableData = FXCollections.observableArrayList();
    private Long selectedLabelId;
    private boolean editingNew = false;

    public LabelsController(LabelService labelService) {
        this.labelService = labelService;
    }

    @FXML
    public void initialize() {
        log.info("LabelsController initialized");

        nameColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
        colorColumn.setCellValueFactory(cd -> {
            String color = cd.getValue().getColor();
            return new SimpleStringProperty(color != null ? color : "");
        });
        colorColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText("\u2588\u2588 " + item);
                    try {
                        setStyle("-fx-text-fill: " + item + ";");
                    } catch (Exception e) {
                        setStyle("");
                    }
                }
            }
        });
        commentColumn.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getComment() != null ? cd.getValue().getComment() : ""));

        labelTable.setItems(tableData);
        loadLabels();

        labelTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !editingNew) {
                selectedLabelId = newVal.getId();
                nameField.setText(newVal.getName() != null ? newVal.getName() : "");
                if (newVal.getColor() != null) {
                    try {
                        colorPicker.setValue(Color.web(newVal.getColor()));
                    } catch (Exception e) {
                        colorPicker.setValue(Color.BLACK);
                    }
                }
                commentField.setText(newVal.getComment() != null ? newVal.getComment() : "");
                saveButton.setDisable(false);
                deleteButton.setDisable(false);
            }
        });
    }

    private void loadLabels() {
        tableData.setAll(labelService.getAllLabels());
    }

    @FXML
    private void onAdd() {
        editingNew = true;
        selectedLabelId = null;
        nameField.clear();
        colorPicker.setValue(Color.web("#1976d2"));
        commentField.clear();
        saveButton.setDisable(false);
        deleteButton.setDisable(true);
        nameField.requestFocus();
    }

    @FXML
    private void onSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError(I18n.get("labels.name.required"));
            return;
        }

        String color = colorToHex(colorPicker.getValue());
        String comment = commentField.getText().trim();

        if (editingNew) {
            labelService.createLabel(name, color, comment.isEmpty() ? null : comment);
            editingNew = false;
        } else if (selectedLabelId != null) {
            labelService.updateLabel(selectedLabelId, name, color, comment.isEmpty() ? null : comment);
        }

        loadLabels();
        clearEditor();
        log.info("Saved label: {}", name);
    }

    @FXML
    private void onDelete() {
        if (selectedLabelId == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.get("labels.delete.confirm"),
                ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(rootPane.getScene().getWindow());

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                labelService.deleteLabel(selectedLabelId);
                selectedLabelId = null;
                loadLabels();
                clearEditor();
            }
        });
    }

    private void clearEditor() {
        nameField.clear();
        commentField.clear();
        saveButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private String colorToHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Error");
        alert.initOwner(rootPane.getScene().getWindow());
        alert.showAndWait();
    }
}
