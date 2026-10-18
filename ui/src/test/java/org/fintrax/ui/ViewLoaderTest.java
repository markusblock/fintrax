package org.fintrax.ui;

import javafx.scene.Parent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ViewLoaderTest extends AbstractUITest {
    private static final String[] SUPPORTED_VIEWS = {
            "main", "bankAccounts", "transactions", "categories", "rules",
            "labels", "activityLog", "settings", "addAccountDialog"
    };

    @Test
    void loadsEveryViewWithSpringPrototypeController() throws Exception {
        ViewLoader viewLoader = applicationContext.getBean(ViewLoader.class);

        for (String view : SUPPORTED_VIEWS) {
            Parent firstRoot = viewLoader.load(view);
            Object firstController = controllerFor(firstRoot);
            Object secondController = controllerFor(viewLoader.load(view));

            assertNotNull(firstController, view);
            assertNotSame(firstController, secondController, view);
            assertNotSame(firstController, applicationContext.getBean(firstController.getClass()), view);
        }
    }

    private Object controllerFor(Parent root) {
        return root.getProperties().values().stream()
                .filter(value -> value.getClass().getPackageName().equals("org.fintrax.ui.controller"))
                .findFirst()
                .orElseThrow();
    }
}
