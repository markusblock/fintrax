package org.fintrax.ui.controller;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import org.fintrax.config.I18n;
import org.fintrax.ui.AbstractUITest;
import org.fintrax.ui.ViewLoader;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    void ruleActionOptionsExcludeNoteEditing() throws TimeoutException {
        clickOn(I18n.get("button.addRule"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> lookup("#createRuleSave").tryQuery().isPresent());

        ComboBox<?> actionType = lookup(".combo-box").queryAll().stream()
                .filter(node -> node instanceof ComboBox<?> combo
                        && combo.getItems().contains(I18n.get("ruleEditor.action.assignCategory")))
                .map(node -> (ComboBox<?>) node)
                .findFirst().orElseThrow();

        assertEquals(List.of(I18n.get("ruleEditor.action.assignCategory"),
                I18n.get("ruleEditor.action.assignLabel")), actionType.getItems());
        assertFalse(actionType.getItems().contains("SET_NOTE"));
        ComboBox<?> matchField = lookup(".combo-box").queryAll().stream()
                .filter(node -> node instanceof ComboBox<?> combo
                        && combo.getItems().contains(I18n.get("ruleEditor.field.payeeName")))
                .map(node -> (ComboBox<?>) node)
                .findFirst().orElseThrow();
        assertEquals(List.of(I18n.get("ruleEditor.field.payeeName"), I18n.get("ruleEditor.field.purpose"),
                I18n.get("ruleEditor.field.amount"), I18n.get("ruleEditor.field.account")), matchField.getItems());
        ComboBox<?> operator = lookup(".combo-box").queryAll().stream()
                .filter(node -> node instanceof ComboBox<?> combo
                        && combo.getItems().contains(I18n.get("ruleEditor.operator.containsNot")))
                .map(node -> (ComboBox<?>) node)
                .findFirst().orElseThrow();
        assertEquals(List.of(I18n.get("ruleEditor.operator.contains"), I18n.get("ruleEditor.operator.containsNot"),
                I18n.get("ruleEditor.operator.is"), I18n.get("ruleEditor.operator.isNot"),
                I18n.get("ruleEditor.operator.matchesRegex"), I18n.get("ruleEditor.operator.doesNotMatchRegex")),
                operator.getItems());
        ComboBox<?> matchMode = lookup("#conditionMatchModeCombo").query();
        assertEquals(List.of(I18n.get("ruleEditor.matchMode.all"),
                I18n.get("ruleEditor.matchMode.any")), matchMode.getItems());
        assertEquals(I18n.get("ruleEditor.matchMode.all"), matchMode.getValue());
        assertEquals(lookup(".rule-condition-group").query(),
                lookup("#addRuleConditionButton").query().getParent());
        assertEquals(lookup(".rule-action-group").query(),
                lookup("#addRuleActionButton").query().getParent());
        assertEquals(lookup(".rule-action-group").query(),
                lookup(".rule-then-label").query().getParent());

        Button cancelButton = lookup("#createRuleCancel").query();
        interact(cancelButton::fire);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
