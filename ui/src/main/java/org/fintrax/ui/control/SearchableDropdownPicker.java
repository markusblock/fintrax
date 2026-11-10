package org.fintrax.ui.control;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fintrax.config.I18n;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Searchable dropdown picker for one or many values, anchored to its source control. */
public final class SearchableDropdownPicker {
    private static Picker<?> activePicker;

    private SearchableDropdownPicker() {}

    public static <T> void showOne(Node anchor, Collection<T> options, T initiallySelected,
                                   Function<T, String> displayText, String createTextKey,
                                   Function<String, T> createOption, Consumer<T> onSave) {
        new Picker<>(anchor, options, initiallySelected == null ? List.of() : List.of(initiallySelected),
                new PickerConfiguration<>(displayText, false, createTextKey, createOption,
                        new MultiSelection<>(item -> false, values -> onSave.accept(
                                values.isEmpty() ? null : values.get(0))))).show();
    }

    public static <T> void showMany(Node anchor, Collection<T> options, Collection<T> initiallySelected,
                                     Function<T, String> displayText, String createTextKey,
                                     Function<String, T> createOption, Consumer<List<T>> onSave) {
        showMany(anchor, options, initiallySelected, displayText, createTextKey, createOption,
                new MultiSelection<>(item -> false, onSave));
    }

    public static <T> void showMany(Node anchor, Collection<T> options, Collection<T> initiallySelected,
                                    Function<T, String> displayText, String createTextKey,
                                    Function<String, T> createOption, MultiSelection<T> selection) {
        new Picker<>(anchor, options, initiallySelected,
                new PickerConfiguration<>(displayText, true, createTextKey, createOption, selection)).show();
    }

    public record MultiSelection<T>(Predicate<T> exclusiveSelection, Consumer<List<T>> onSave) {
    }

    private record PickerConfiguration<T>(Function<T, String> displayText, boolean multiple,
                                          String createTextKey, Function<String, T> createOption,
                                          MultiSelection<T> selection) {
    }

    private static final class Picker<T> {
        private static final double MAX_LIST_HEIGHT = 220;

        private final Node anchor;
        private final List<T> options;
        private final Function<T, String> displayText;
        private final boolean multiple;
        private final String createTextKey;
        private final Function<String, T> createOption;
        private final Predicate<T> exclusiveSelection;
        private final Consumer<List<T>> onSave;
        private final Set<T> selected;
        private final ContextMenu popup = new ContextMenu();
        private final TextField searchField = new TextField();
        private final ListView<T> optionsList = new ListView<>();
        private final Button createButton = new Button();
        private final Button clearButton = new Button();
        private final Button deselectAllButton = new Button();
        private final Button saveButton = new Button();
        private final Label errorLabel = new Label();
        private final Label keyboardHelp = new Label();

        private Picker(Node anchor, Collection<T> options, Collection<T> initiallySelected,
                       PickerConfiguration<T> configuration) {
            this.anchor = anchor;
            this.displayText = configuration.displayText();
            this.multiple = configuration.multiple();
            this.createTextKey = configuration.createTextKey();
            this.createOption = configuration.createOption();
            this.exclusiveSelection = configuration.selection().exclusiveSelection();
            this.onSave = configuration.selection().onSave();
            Comparator<String> naturalOrder = NaturalOrder.comparator(I18n.getCurrentLocale());
            this.options = options.stream()
                    .sorted(Comparator.comparing((T option) -> !exclusiveSelection.test(option))
                            .thenComparing(displayText, naturalOrder))
                    .toList();
            this.selected = new LinkedHashSet<>(initiallySelected);
        }

        private void show() {
            if (activePicker != null) {
                Picker<?> current = activePicker;
                boolean sameAnchor = current.anchor == anchor;
                if (current.popup.isShowing()) current.popup.hide();
                activePicker = null;
                if (sameAnchor) return;
            }
            if (anchor.getScene() == null) return;
            activePicker = this;

            searchField.setId("pickerSearch");
            searchField.setPromptText(I18n.get("picker.search.prompt"));
            searchField.setAccessibleText(I18n.get("picker.search.accessible"));
            searchField.setPrefHeight(34);
            optionsList.setId("pickerList");
            optionsList.setPlaceholder(new Label(I18n.get("picker.noResults")));
            optionsList.setPrefHeight(180);
            optionsList.setCellFactory(list -> createCell());
            createButton.setId("pickerCreateButton");
            createButton.setMaxWidth(Double.MAX_VALUE);
            createButton.getStyleClass().add("button-outlined");
            clearButton.setId("pickerClear");
            clearButton.setText(I18n.get("picker.category.clear"));
            deselectAllButton.setId("pickerDeselectAll");
            deselectAllButton.getStyleClass().add("button-outlined");
            saveButton.setId("pickerSave");
            saveButton.setText(I18n.get("picker.select"));
            saveButton.setDefaultButton(true);
            errorLabel.getStyleClass().add("error-message");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            keyboardHelp.setText(I18n.get(multiple
                    ? "picker.keyboard.labels" : "picker.keyboard.category"));
            keyboardHelp.getStyleClass().add("picker-keyboard-help");

            HBox searchRow = new HBox(4, searchField);
            HBox.setHgrow(searchField, Priority.ALWAYS);
            Button clearSearch = new Button("×");
            clearSearch.setId("pickerSearchClear");
            clearSearch.setAccessibleText(I18n.get("picker.search.clear"));
            clearSearch.getStyleClass().add("picker-search-clear");
            searchRow.getChildren().add(clearSearch);
            VBox content = new VBox(7, searchRow, optionsList, keyboardHelp, createButton, errorLabel);
            content.setId("searchableDropdownPicker");
            content.getStyleClass().add("searchable-dropdown-popup");
            content.setPadding(new Insets(8));
            content.setPrefWidth(Math.max(320, Math.min(420, anchor.getBoundsInLocal().getWidth())));

            HBox actions = new HBox(8);
            actions.getStyleClass().add("searchable-dropdown-actions");
            if (multiple) {
                actions.getChildren().addAll(deselectAllButton, saveButton);
                HBox.setHgrow(saveButton, Priority.ALWAYS);
                saveButton.setMaxWidth(Double.MAX_VALUE);
                content.getChildren().add(actions);
            } else if (!selected.isEmpty()) {
                actions.getChildren().add(clearButton);
                content.getChildren().add(actions);
            }

            popup.getItems().setAll(new CustomMenuItem(content, false));
            popup.setAutoFix(true);
            popup.setAutoHide(true);
            popup.setHideOnEscape(false);
            popup.setOnHidden(event -> Platform.runLater(() -> {
                if (activePicker == this) activePicker = null;
                if (anchor.getScene() != null) anchor.requestFocus();
            }));

            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                errorLabel.setText("");
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
                updateOptions(false);
            });
            searchField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSearchKey);
            optionsList.addEventFilter(KeyEvent.KEY_TYPED, this::handleListTyping);
            optionsList.setOnKeyPressed(this::handleListKey);
            optionsList.setOnMouseClicked(event -> {
                T clicked = listItemAt(event.getTarget());
                if (clicked == null) return;
                if (multiple) {
                    if (isInsideCheckBox(event.getTarget())) return;
                    toggle(clicked);
                } else {
                    selectOne(clicked);
                }
                event.consume();
            });
            clearSearch.setOnAction(event -> {
                searchField.clear();
                searchField.requestFocus();
            });
            createButton.setOnAction(event -> createFromSearch());
            clearButton.setOnAction(event -> save(List.of()));
            deselectAllButton.setOnAction(event -> deselectAll());
            saveButton.setOnAction(event -> save(List.copyOf(selected)));

            updateOptions(true);
            popup.show(anchor, Side.BOTTOM, 0, 0);
            popup.getScene().getStylesheets().setAll(anchor.getScene().getStylesheets());
            Platform.runLater(searchField::requestFocus);
        }

        private ListCell<T> createCell() {
            return new ListCell<>() {
                @Override
                protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setTooltip(null);
                        return;
                    }
                    setText(null);
                    setTooltip(new Tooltip(displayText.apply(item)));
                    if (multiple) {
                        CheckBox checkBox = new CheckBox();
                        checkBox.setSelected(selected.contains(item));
                        checkBox.setFocusTraversable(false);
                        checkBox.setOnAction(event -> toggle(item));
                        Label text = new Label(displayText.apply(item));
                        text.setMaxWidth(Double.MAX_VALUE);
                        text.setTextOverrun(OverrunStyle.ELLIPSIS);
                        HBox option = new HBox(8, checkBox, text);
                        HBox.setHgrow(text, Priority.ALWAYS);
                        setGraphic(option);
                    } else {
                        Label marker = new Label(selected.contains(item) ? "✓" : "");
                        marker.getStyleClass().add("picker-current-marker");
                        Label text = new Label(displayText.apply(item));
                        text.setMaxWidth(Double.MAX_VALUE);
                        text.setTextOverrun(OverrunStyle.ELLIPSIS);
                        HBox option = new HBox(8, marker, text);
                        HBox.setHgrow(text, Priority.ALWAYS);
                        setGraphic(option);
                    }
                }
            };
        }

        private void updateOptions(boolean preferCurrent) {
            String query = searchField.getText() == null ? "" : searchField.getText().trim();
            String normalizedQuery = query.toLowerCase(Locale.ROOT);
            List<T> filtered = options.stream()
                    .filter(item -> displayText.apply(item).toLowerCase(Locale.ROOT).contains(normalizedQuery))
                    .toList();
            optionsList.setItems(FXCollections.observableArrayList(filtered));
            T current = selected.stream().findFirst().orElse(null);
            T currentMatch = current == null ? null
                    : filtered.stream().filter(item -> Objects.equals(item, current)).findFirst().orElse(null);
            if (!multiple && preferCurrent && currentMatch != null) {
                optionsList.getSelectionModel().select(currentMatch);
            } else {
                optionsList.getSelectionModel().selectFirst();
            }
            optionsList.setPrefHeight(Math.min(MAX_LIST_HEIGHT, Math.max(58, filtered.size() * 29.0)));

            boolean exactMatch = options.stream()
                    .anyMatch(item -> displayText.apply(item).equalsIgnoreCase(query));
            boolean canCreate = createOption != null && !query.isEmpty() && !exactMatch;
            createButton.setText(canCreate ? I18n.get(createTextKey, query) : "");
            createButton.setVisible(canCreate);
            createButton.setManaged(canCreate);
            optionsList.refresh();
            updateDeselectAllButton();
        }

        private void handleSearchKey(KeyEvent event) {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.UP) {
                if (!optionsList.getItems().isEmpty()) {
                    int selectedIndex = optionsList.getSelectionModel().getSelectedIndex();
                    int nextIndex = selectedIndex < 0 ? 0 : selectedIndex
                            + (event.getCode() == KeyCode.DOWN ? 1 : -1);
                    nextIndex = Math.max(0, Math.min(nextIndex, optionsList.getItems().size() - 1));
                    optionsList.getSelectionModel().select(nextIndex);
                    optionsList.requestFocus();
                }
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                finishFromKeyboard();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                event.consume();
            }
        }

        private void handleListKey(KeyEvent event) {
            if (event.getCode() == KeyCode.ENTER) {
                finishFromKeyboard();
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE && multiple) {
                T current = optionsList.getSelectionModel().getSelectedItem();
                if (current != null) toggle(current);
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                event.consume();
            }
        }

        private void handleListTyping(KeyEvent event) {
            String character = event.getCharacter();
            if (character == null || character.isEmpty()
                    || character.codePoints().allMatch(Character::isISOControl)) return;
            searchField.appendText(character);
            searchField.requestFocus();
            event.consume();
        }

        private void finishFromKeyboard() {
            if (multiple) {
                save(List.copyOf(selected));
            } else {
                T current = optionsList.getSelectionModel().getSelectedItem();
                if (current != null) selectOne(current);
            }
        }

        private void selectOne(T item) {
            save(List.of(item));
        }

        private void toggle(T item) {
            if (!selected.remove(item)) addSelection(item);
            optionsList.refresh();
            updateDeselectAllButton();
        }

        private void addSelection(T item) {
            if (exclusiveSelection.test(item)) selected.clear();
            else selected.removeIf(exclusiveSelection);
            selected.add(item);
        }

        private boolean isInsideCheckBox(Object target) {
            if (!(target instanceof Node node)) return false;
            while (node != null) {
                if (node instanceof CheckBox) return true;
                if (node instanceof ListCell<?>) return false;
                node = node.getParent();
            }
            return false;
        }

        private void deselectAll() {
            selected.clear();
            optionsList.refresh();
            updateDeselectAllButton();
        }

        private void updateDeselectAllButton() {
            deselectAllButton.setText(I18n.get("picker.labels.deselectAll"));
            deselectAllButton.setDisable(selected.isEmpty());
        }

        private void createFromSearch() {
            try {
                T created = createOption.apply(searchField.getText().trim());
                if (multiple) {
                    addSelection(created);
                    searchField.clear();
                    searchField.requestFocus();
                } else {
                    selectOne(created);
                }
            } catch (RuntimeException exception) {
                errorLabel.setText(exception.getMessage());
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            }
        }

        private void save(List<T> values) {
            onSave.accept(values);
            popup.hide();
        }

        @SuppressWarnings("unchecked")
        private T listItemAt(Object target) {
            if (!(target instanceof Node node)) return null;
            while (node != null) {
                if (node instanceof ListCell<?> cell) return (T) cell.getItem();
                node = node.getParent();
            }
            return null;
        }
    }
}
