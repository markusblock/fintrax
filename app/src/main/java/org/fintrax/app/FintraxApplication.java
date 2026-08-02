package org.fintrax.app;

import org.fintrax.service.ServiceRegistry;
import org.fintrax.ui.FintraxUI;
import org.fintrax.config.I18n;

public final class FintraxApplication {
    public static void main(String[] args) {
        ServiceRegistry.initialize();
        I18n.setLocale(ServiceRegistry.getInstance().getSettingsService().getLocale());
        Runtime.getRuntime().addShutdownHook(new Thread(ServiceRegistry::shutdown));
        FintraxUI.main(args);
        System.exit(0);
    }
}
