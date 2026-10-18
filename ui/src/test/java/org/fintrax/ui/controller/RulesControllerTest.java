package org.fintrax.ui.controller;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.ui.AbstractUITest;
import org.fintrax.ui.ViewLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RulesControllerTest extends AbstractUITest {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = applicationContext.getBean(ViewLoader.class).load("rules");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    @Test
    void ruleActionOptionsExcludeNoteEditing() {
        clickOn(I18n.get("button.addRule"));

        ComboBox<?> actionType = lookup(".combo-box").queryAll().stream()
                .filter(node -> node instanceof ComboBox<?> combo
                        && combo.getItems().contains("ASSIGN_CATEGORY"))
                .map(node -> (ComboBox<?>) node)
                .findFirst().orElseThrow();

        assertEquals(List.of("ASSIGN_CATEGORY", "ADD_LABEL"), actionType.getItems());
        assertFalse(actionType.getItems().contains("SET_NOTE"));
    }
}
