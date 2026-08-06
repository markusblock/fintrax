package org.fintrax.app;

import org.springframework.context.ConfigurableApplicationContext;
import org.fintrax.ui.FintraxUI;

public final class FintraxApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context = FintraxSpringBootstrap.start(args)) {
            FintraxUI.launch(context, args);
        }
        System.exit(0);
    }
}
