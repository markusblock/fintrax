package org.fintrax.ui;

import org.fintrax.ui.controller.SessionViewState;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class SessionViewStateResetExtension implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        if (AbstractUITest.applicationContext != null) {
            AbstractUITest.applicationContext.getBean(SessionViewState.class).clear();
        }
    }
}
