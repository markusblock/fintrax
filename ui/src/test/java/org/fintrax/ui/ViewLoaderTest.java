package org.fintrax.ui;

import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import org.fintrax.ui.controller.AddAccountDialogController;
import org.fintrax.ui.controller.ActivityLogController;
import org.fintrax.ui.controller.BankAccountsController;
import org.fintrax.ui.controller.CategoriesController;
import org.fintrax.ui.controller.LabelsController;
import org.fintrax.ui.controller.MainController;
import org.fintrax.ui.controller.RulesController;
import org.fintrax.ui.controller.SettingsController;
import org.fintrax.ui.controller.TransactionsController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ViewLoaderTest extends AbstractUITest {
    private static final String[] SUPPORTED_VIEWS = {
            "main", "bankAccounts", "transactions", "categories", "rules",
            "labels", "activityLog", "settings", "addAccountDialog"
    };

    @Test
    void loadsEverySupportedFxmlResource() throws Exception {
        ViewLoader viewLoader = applicationContext.getBean(ViewLoader.class);

        for (String view : SUPPORTED_VIEWS) {
            Parent root = viewLoader.load(view);
            assertNotNull(root, view);
        }
    }

    @Test
    void createsPrototypeControllerForEachLoad() throws Exception {
        ViewLoader viewLoader = applicationContext.getBean(ViewLoader.class);
        MainController first = (MainController) ((BorderPane) viewLoader.load("main"))
                .getProperties().get(MainController.class.getName());
        MainController second = (MainController) ((BorderPane) viewLoader.load("main"))
                .getProperties().get(MainController.class.getName());

        assertNotSame(first, second);
    }

    @Test
    void createsBankAccountsControllerThroughSpring() throws Exception {
        ViewLoader viewLoader = applicationContext.getBean(ViewLoader.class);

        BorderPane firstRoot = (BorderPane) viewLoader.load("bankAccounts");
        BorderPane secondRoot = (BorderPane) viewLoader.load("bankAccounts");

        BankAccountsController first = (BankAccountsController) firstRoot.getProperties()
                .get(BankAccountsController.class.getName());
        BankAccountsController second = (BankAccountsController) secondRoot.getProperties()
                .get(BankAccountsController.class.getName());

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
        assertNotSame(first, applicationContext.getBean(BankAccountsController.class));
    }

    @Test
    void createsTransactionsControllerThroughSpring() throws Exception {
        ViewLoader viewLoader = applicationContext.getBean(ViewLoader.class);

        BorderPane firstRoot = (BorderPane) viewLoader.load("transactions");
        BorderPane secondRoot = (BorderPane) viewLoader.load("transactions");

        TransactionsController first = (TransactionsController) firstRoot.getProperties()
                .get(TransactionsController.class.getName());
        TransactionsController second = (TransactionsController) secondRoot.getProperties()
                .get(TransactionsController.class.getName());

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
        assertNotSame(first, applicationContext.getBean(TransactionsController.class));
    }

    @Test
    void createsCategoriesLabelsAndRulesControllersThroughSpring() throws Exception {
        ViewLoader viewLoader = applicationContext.getBean(ViewLoader.class);

        CategoriesController firstCategories = (CategoriesController) ((BorderPane) viewLoader.load("categories"))
                .getProperties().get(CategoriesController.class.getName());
        CategoriesController secondCategories = (CategoriesController) ((BorderPane) viewLoader.load("categories"))
                .getProperties().get(CategoriesController.class.getName());
        LabelsController labels = (LabelsController) ((BorderPane) viewLoader.load("labels"))
                .getProperties().get(LabelsController.class.getName());
        RulesController rules = (RulesController) ((BorderPane) viewLoader.load("rules"))
                .getProperties().get(RulesController.class.getName());

        assertNotNull(firstCategories);
        assertNotNull(labels);
        assertNotNull(rules);
        assertNotSame(firstCategories, secondCategories);
        assertNotSame(firstCategories, applicationContext.getBean(CategoriesController.class));
        assertNotSame(labels, applicationContext.getBean(LabelsController.class));
        assertNotSame(rules, applicationContext.getBean(RulesController.class));
    }

    @Test
    void createsAddAccountDialogControllerThroughSpring() throws Exception {
        ViewLoader viewLoader = applicationContext.getBean(ViewLoader.class);

        Parent root = viewLoader.load("addAccountDialog");

        assertNotNull(root.getProperties().get(AddAccountDialogController.class.getName()));
    }

    @Test
    void createsSettingsAndActivityLogControllersThroughSpring() throws Exception {
        ViewLoader viewLoader = applicationContext.getBean(ViewLoader.class);

        SettingsController firstSettings = (SettingsController) ((BorderPane) viewLoader.load("settings"))
                .getProperties().get(SettingsController.class.getName());
        SettingsController secondSettings = (SettingsController) ((BorderPane) viewLoader.load("settings"))
                .getProperties().get(SettingsController.class.getName());
        ActivityLogController activityLog = (ActivityLogController) ((BorderPane) viewLoader.load("activityLog"))
                .getProperties().get(ActivityLogController.class.getName());

        assertNotNull(firstSettings);
        assertNotNull(activityLog);
        assertNotSame(firstSettings, secondSettings);
        assertNotSame(firstSettings, applicationContext.getBean(SettingsController.class));
        assertNotSame(activityLog, applicationContext.getBean(ActivityLogController.class));
    }

}
