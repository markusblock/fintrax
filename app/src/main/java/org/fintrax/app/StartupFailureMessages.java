package org.fintrax.app;

import org.fintrax.config.I18n;

import java.nio.file.Path;

final class StartupFailureMessages {
    private StartupFailureMessages() {
    }

    static String storageInUse(Path storagePath) {
        return I18n.get("startup.storageInUse.message", storagePath);
    }
}
