package org.fintrax.ui.controller;

import javafx.stage.Window;
import org.fintrax.model.Transaction;
import org.fintrax.service.RuleService;
import org.fintrax.ui.ViewLoader;

final class CreateRuleFromTransactionDialog {
    private CreateRuleFromTransactionDialog() {}

    static void show(Window owner, Transaction transaction, RuleService ruleService, ViewLoader viewLoader) {
        RuleEditorDialog.show(owner, viewLoader, "dialog.createRule.title", "dialog.createRule.header",
                        "createRuleSave", "createRuleCancel", editor -> editor.setFromTransaction(transaction))
                .ifPresent(input -> ruleService.createRule(input.name(), input.conditions(), input.actions()));
    }
}
