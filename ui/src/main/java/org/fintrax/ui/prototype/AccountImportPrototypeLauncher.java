package org.fintrax.ui.prototype;

import javafx.application.Application;

/** Launches the prototype without JavaFX's special Application-subclass launcher path. */
public final class AccountImportPrototypeLauncher {
    private AccountImportPrototypeLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(AccountImportWizardPrototype.class, args);
    }
}
