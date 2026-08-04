package org.fintrax.ui;

import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import org.fintrax.ui.controller.MainController;
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
}
