package org.fintrax.app;

import org.fintrax.service.ServiceRegistry;
import org.fintrax.ui.FintraxUI;

public final class FintraxApplication {
    public static void main(String[] args) {
        ServiceRegistry.initialize();
        Runtime.getRuntime().addShutdownHook(new Thread(ServiceRegistry::shutdown));
        FintraxUI.main(args);
        System.exit(0);
    }
}
