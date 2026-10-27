package org.fintrax.ui.control;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.fintrax.config.I18n;
import org.fintrax.ui.FintraxUI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;

public final class SearchableListPicker {
    private SearchableListPicker() {}

    public static <T> Optional<List<T>> chooseOne(Window owner, String title,
                                                   Collection<T> options, T initiallySelected,
                                                   Function<T, String> displayText,
                                                   String createTextKey, Function<String, T> createOption) {
        Dialog<List<T>> dialog = createDialog(owner, title);
        TextField searchField = createSearchField();
        ListView<T> optionsList = createList(options, displayText);
        Button createButton = createButton();
        Label errorLabel = createErrorLabel();
        ButtonType saveType = new ButtonType(I18n.get("button.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveType, ButtonType.CANCEL);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.setId("pickerSave");
        saveButton.setDefaultButton(true);
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setId("pickerCancel");
        SingleSelection<T> selection = new SingleSelection<>(initiallySelected);
        saveButton.setDisable(!selection.isValid());
        VBox content = createContent(searchField, optionsList, createButton, errorLabel);

        optionsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayText.apply(item));
            }
        });
        optionsList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                selection.select(newValue);
                saveButton.setDisable(false);
            }
        });
        AtomicReference<T> selectionBeforeClick = new AtomicReference<>(initiallySelected);
        optionsList.addEventFilter(MouseEvent.MOUSE_PRESSED,
                event -> selectionBeforeClick.set(selection.value()));
        optionsList.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            T clickedItem = listItemAt(event.getTarget());
            if (clickedItem != null && Objects.equals(selectionBeforeClick.get(), clickedItem)) {
                selection.clear();
                optionsList.getSelectionModel().clearSelection();
                saveButton.setDisable(false);
                saveButton.requestFocus();
                event.consume();
            }
        });
        createButton.setOnAction(event -> {
            try {
                T created = createOption.apply(searchField.getText().trim());
                selection.select(created);
                saveButton.setDisable(false);
                saveButton.requestFocus();
            } catch (RuntimeException exception) {
                showCreationError(errorLabel, exception);
            }
        });

        updateSearch(options, displayText, searchField, optionsList, createButton, createTextKey);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            hideCreationError(errorLabel);
            updateSearch(options, displayText, searchField, optionsList, createButton, createTextKey);
            String query = newValue == null ? "" : newValue.trim();
            List<T> exactMatches = options.stream()
                    .filter(item -> displayText.apply(item).equalsIgnoreCase(query))
                    .toList();
            if (query.isEmpty()) {
                if (initiallySelected != null) {
                    optionsList.getSelectionModel().select(initiallySelected);
                    selection.select(initiallySelected);
                    saveButton.setDisable(false);
                } else {
                    optionsList.getSelectionModel().clearSelection();
                    selection.invalidate();
                    saveButton.setDisable(true);
                }
            } else if (exactMatches.size() == 1) {
                optionsList.getSelectionModel().select(exactMatches.get(0));
                selection.select(exactMatches.get(0));
                saveButton.setDisable(false);
                saveButton.requestFocus();
            } else {
                optionsList.getSelectionModel().clearSelection();
                selection.invalidate();
                saveButton.setDisable(true);
            }
        });
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(buttonType -> buttonType == saveType && selection.isValid()
                ? selection.value() == null ? List.of() : List.of(selection.value())
                : null);
        optionsList.getSelectionModel().select(initiallySelected);
        installSingleSelectionKeyboard(dialog.getDialogPane(), searchField, saveButton);
        dialog.setOnShown(event -> Platform.runLater(() -> {
            dialog.getDialogPane().getScene().getWindow().requestFocus();
            Platform.runLater(() -> {
                if (selection.isValid()) saveButton.requestFocus();
                else searchField.requestFocus();
            });
        }));
        return dialog.showAndWait();
    }

    public static <T> Optional<List<T>> chooseMany(Window owner, String title,
                                                    Collection<T> options, Collection<T> initiallySelected,
                                                    Function<T, String> displayText,
                                                    String createTextKey, Function<String, T> createOption) {
        Dialog<List<T>> dialog = createDialog(owner, title);
        TextField searchField = createSearchField();
        ListView<T> optionsList = createList(options, displayText);
        Button createButton = createButton();
        Label errorLabel = createErrorLabel();
        FlowPane selectedPane = new FlowPane(6, 6);
        selectedPane.setId("selectedLabelsPane");
        selectedPane.setPadding(new Insets(2, 0, 4, 0));
        List<T> selected = new ArrayList<>(initiallySelected);
        VBox content = createContent(searchField, optionsList, createButton, errorLabel);
        content.getChildren().add(1, selectedPane);

        optionsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                CheckBox checkBox = new CheckBox();
                checkBox.setSelected(selected.contains(item));
                checkBox.setFocusTraversable(false);
                checkBox.setOnAction(event -> {
                    if (checkBox.isSelected()) {
                        if (!selected.contains(item)) selected.add(item);
                    } else {
                        selected.remove(item);
                    }
                    renderSelected(selectedPane, selected, displayText, optionsList);
                });
                HBox option = new HBox(8, checkBox, labelDisplay(item, displayText));
                option.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                option.setOnMouseClicked(event -> {
                    if (event.getTarget() != checkBox) checkBox.fire();
                });
                setText(null);
                setGraphic(option);
            }
        });
        createButton.setOnAction(event -> {
            try {
                T created = createOption.apply(searchField.getText().trim());
                if (!selected.contains(created)) selected.add(created);
                searchField.clear();
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
                renderSelected(selectedPane, selected, displayText, optionsList);
            } catch (RuntimeException exception) {
                showCreationError(errorLabel, exception);
            }
        });

        renderSelected(selectedPane, selected, displayText, optionsList);
        updateSearch(options, displayText, searchField, optionsList, createButton, createTextKey);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            hideCreationError(errorLabel);
            updateSearch(options, displayText, searchField, optionsList, createButton, createTextKey);
        });
        dialog.getDialogPane().setContent(content);
        ButtonType saveType = new ButtonType(I18n.get("button.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(saveType).setId("pickerSave");
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setId("pickerCancel");
        dialog.setResultConverter(buttonType -> buttonType == saveType ? List.copyOf(selected) : null);
        focusSearchWhenShown(dialog, searchField);
        return dialog.showAndWait();
    }

    private static <T> Dialog<List<T>> createDialog(Window owner, String title) {
        Dialog<List<T>> dialog = new Dialog<>();
        FintraxUI.configureDialog(dialog);
        dialog.setTitle(title);
        dialog.initOwner(owner);
        dialog.setResizable(true);
        return dialog;
    }

    private static TextField createSearchField() {
        TextField searchField = new TextField();
        searchField.setId("pickerSearch");
        searchField.setPromptText(I18n.get("picker.search.prompt"));
        return searchField;
    }

    private static <T> ListView<T> createList(Collection<T> options, Function<T, String> displayText) {
        List<T> sorted = options.stream()
                .sorted(Comparator.comparing(displayText, String.CASE_INSENSITIVE_ORDER))
                .toList();
        ListView<T> list = new ListView<>(FXCollections.observableArrayList(sorted));
        list.setId("pickerList");
        list.setPrefHeight(260);
        list.setPlaceholder(new Label(I18n.get("picker.noResults")));
        return list;
    }

    private static Button createButton() {
        Button button = new Button();
        button.setId("pickerCreateButton");
        button.getStyleClass().add("button-outlined");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setVisible(false);
        button.setManaged(false);
        return button;
    }

    private static Label createErrorLabel() {
        Label label = new Label();
        label.getStyleClass().add("error-message");
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    private static VBox createContent(TextField searchField, ListView<?> optionsList,
                                      Button createButton, Label errorLabel) {
        VBox content = new VBox(8, searchField, optionsList, createButton, errorLabel);
        content.setMinWidth(350);
        content.setPrefWidth(420);
        content.getStyleClass().add("searchable-picker-dialog");
        return content;
    }

    private static <T> void updateSearch(Collection<T> options, Function<T, String> displayText,
                                         TextField searchField, ListView<T> optionsList,
                                         Button createButton, String createTextKey) {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        String normalized = query.toLowerCase(Locale.ROOT);
        List<T> filtered = options.stream()
                .filter(item -> displayText.apply(item).toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(displayText, String.CASE_INSENSITIVE_ORDER))
                .toList();
        optionsList.setItems(FXCollections.observableArrayList(filtered));

        boolean exactMatch = options.stream()
                .anyMatch(item -> displayText.apply(item).equalsIgnoreCase(query));
        boolean canCreate = !query.isEmpty() && !exactMatch;
        createButton.setText(canCreate ? I18n.get(createTextKey, query) : "");
        createButton.setVisible(canCreate);
        createButton.setManaged(canCreate);
    }

    private static <T> void renderSelected(FlowPane selectedPane, List<T> selected,
                                           Function<T, String> displayText, ListView<T> optionsList) {
        selectedPane.getChildren().clear();
        for (T item : List.copyOf(selected)) {
            Node name = labelDisplay(item, displayText);
            Button remove = new Button("×");
            remove.setAccessibleText(I18n.get("picker.remove", displayText.apply(item)));
            remove.getStyleClass().add("selection-chip-remove");
            HBox chip = new HBox(4, name, remove);
            chip.getStyleClass().add("selection-chip");
            remove.setOnAction(event -> {
                selected.remove(item);
                renderSelected(selectedPane, selected, displayText, optionsList);
            });
            selectedPane.getChildren().add(chip);
        }
        optionsList.refresh();
    }

    private static <T> Node labelDisplay(T item, Function<T, String> displayText) {
        if (item instanceof org.fintrax.model.Label label) {
            return LabelChipFactory.create(label);
        }
        return new Label(displayText.apply(item));
    }

    private static void showCreationError(Label errorLabel, RuntimeException exception) {
        errorLabel.setText(exception.getMessage());
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private static void hideCreationError(Label errorLabel) {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private static void installSingleSelectionKeyboard(DialogPane dialogPane, TextField searchField,
                                                       Button saveButton) {
        dialogPane.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!saveButton.isFocused()) return;
            String character = event.getCharacter();
            if (character == null || character.isEmpty()
                    || character.codePoints().allMatch(Character::isISOControl)) return;
            searchField.appendText(character);
            event.consume();
        });
        dialogPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !saveButton.isDisabled()) {
                saveButton.fire();
                event.consume();
            } else if (event.getCode() == KeyCode.BACK_SPACE && saveButton.isFocused()) {
                searchField.deletePreviousChar();
                event.consume();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T listItemAt(Object target) {
        if (!(target instanceof Node node)) return null;
        while (node != null) {
            if (node instanceof ListCell<?> cell) return (T) cell.getItem();
            node = node.getParent();
        }
        return null;
    }

    private static final class SingleSelection<T> {
        private T value;
        private boolean valid;

        private SingleSelection(T initialValue) {
            value = initialValue;
            valid = initialValue != null;
        }

        private T value() {
            return value;
        }

        private boolean isValid() {
            return valid;
        }

        private void select(T selected) {
            value = selected;
            valid = true;
        }

        private void clear() {
            value = null;
            valid = true;
        }

        private void invalidate() {
            value = null;
            valid = false;
        }
    }

    private static void focusSearchWhenShown(Dialog<?> dialog, TextField searchField) {
        dialog.setOnShown(event -> Platform.runLater(searchField::requestFocus));
    }
}
