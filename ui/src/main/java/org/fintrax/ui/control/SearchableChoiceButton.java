package org.fintrax.ui.control;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.fintrax.config.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

public final class SearchableChoiceButton extends Button {
    private final StringProperty value = new SimpleStringProperty(this, "value");
    private final StringProperty promptText = new SimpleStringProperty(this, "promptText", "");
    private final TextField searchField = new TextField();
    private final ListView<String> choicesList = new ListView<>();
    private final Button createButton = new Button();
    private final Label errorLabel = new Label();
    private final ContextMenu popup = new ContextMenu();
    private List<String> choices = List.of();
    private String createTextKey;
    private UnaryOperator<String> createChoice;

    public SearchableChoiceButton() {
        getStyleClass().add("searchable-choice-button");
        setMaxWidth(Double.MAX_VALUE);
        Region arrow = new Region();
        arrow.getStyleClass().add("searchable-choice-arrow");
        arrow.setMinSize(8, 5);
        arrow.setPrefSize(8, 5);
        arrow.setMaxSize(8, 5);
        setGraphic(arrow);
        setContentDisplay(ContentDisplay.RIGHT);
        value.addListener((observable, oldValue, newValue) -> updateButtonText());
        promptText.addListener((observable, oldValue, newValue) -> updateButtonText());
        updateButtonText();

        searchField.setPromptText(I18n.get("picker.search.prompt"));
        searchField.setId("searchableChoiceSearch");
        choicesList.setPrefHeight(220);
        choicesList.setId("searchableChoiceList");
        choicesList.setPlaceholder(new Label(I18n.get("picker.noResults")));
        createButton.setId("searchableChoiceCreate");
        createButton.setMaxWidth(Double.MAX_VALUE);
        createButton.setVisible(false);
        createButton.setManaged(false);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        VBox popupContent = new VBox(6, searchField, choicesList, createButton, errorLabel);
        popupContent.setPadding(new Insets(8));
        popupContent.setPrefWidth(280);
        CustomMenuItem contentItem = new CustomMenuItem(popupContent, false);
        popup.getItems().setAll(contentItem);
        popup.setOnHidden(event -> searchField.clear());

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            updateChoices();
        });
        searchField.setOnKeyPressed(this::handleSearchKey);
        choicesList.setOnKeyPressed(this::handleListKey);
        createButton.setOnAction(event -> createChoiceFromSearch());
        choicesList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) selectCurrentChoice();
        });
        addEventHandler(javafx.event.ActionEvent.ACTION, event -> showChoices());
    }

    public String getValue() {
        return value.get();
    }

    public void setValue(String choice) {
        value.set(choice);
    }

    public StringProperty valueProperty() {
        return value;
    }

    public String getPromptText() {
        return promptText.get();
    }

    public void setPromptText(String text) {
        promptText.set(text);
    }

    public void setChoices(Collection<String> choices) {
        this.choices = List.copyOf(choices);
        if (getValue() != null && !this.choices.contains(getValue())) setValue(null);
        updateChoices();
    }

    public void setCreateChoice(String textKey, UnaryOperator<String> createChoice) {
        this.createTextKey = textKey;
        this.createChoice = createChoice;
        updateChoices();
    }

    private void showChoices() {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        searchField.clear();
        updateChoices();
        popup.show(this, Side.BOTTOM, 0, 0);
        Platform.runLater(searchField::requestFocus);
    }

    private void updateButtonText() {
        String selected = getValue();
        setText(selected == null || selected.isBlank() ? getPromptText() : selected);
    }

    private void updateChoices() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<String> filtered = choices.stream()
                .filter(choice -> choice.toLowerCase(Locale.ROOT).contains(query))
                .toList();
        choicesList.setItems(FXCollections.observableArrayList(filtered));
        boolean exactMatch = choices.stream().anyMatch(choice -> choice.equalsIgnoreCase(query));
        boolean canCreate = createChoice != null && !query.isEmpty() && !exactMatch;
        createButton.setText(canCreate ? I18n.get(createTextKey, searchField.getText().trim()) : "");
        createButton.setVisible(canCreate);
        createButton.setManaged(canCreate);
        if (getValue() != null && filtered.contains(getValue())) {
            choicesList.getSelectionModel().select(getValue());
        } else if (!filtered.isEmpty()) {
            choicesList.getSelectionModel().selectFirst();
        } else {
            choicesList.getSelectionModel().clearSelection();
        }
    }

    private void handleSearchKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            selectCurrentChoice();
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN && !choicesList.getItems().isEmpty()) {
            choicesList.requestFocus();
            choicesList.getSelectionModel().selectFirst();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            popup.hide();
            event.consume();
        }
    }

    private void handleListKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            selectCurrentChoice();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            popup.hide();
            event.consume();
        }
    }

    private void selectCurrentChoice() {
        String selected = choicesList.getSelectionModel().getSelectedItem();
        if (selected != null) setValue(selected);
        popup.hide();
    }

    private void createChoiceFromSearch() {
        String name = searchField.getText().trim();
        if (name.isEmpty() || createChoice == null) return;
        try {
            String created = createChoice.apply(name);
            List<String> updatedChoices = new ArrayList<>(choices);
            updatedChoices.add(created);
            setChoices(updatedChoices);
            setValue(created);
            popup.hide();
        } catch (RuntimeException exception) {
            errorLabel.setText(exception.getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }
}
