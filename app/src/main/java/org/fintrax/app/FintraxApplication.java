package org.fintrax.app;

import org.springframework.context.ConfigurableApplicationContext;
import org.fintrax.service.SettingsService;
import org.fintrax.ui.FintraxUI;
import org.fintrax.config.I18n;

public final class FintraxApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context = FintraxSpringBootstrap.start(args)) {
            SettingsService settingsService = context.getBean(SettingsService.class);
            I18n.setLocale(settingsService.getLocale());
            FintraxUI.configure(context);
            FintraxUI.main(args);
        }
        System.exit(0);
    }
}
