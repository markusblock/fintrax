package org.fintrax.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;
import org.fintrax.store.StorageAlreadyInUseException;
import org.fintrax.ui.FintraxUI;

import java.nio.file.Path;

@Slf4j
public final class FintraxApplication {
    public static void main(String[] args) {
        try {
            try (ConfigurableApplicationContext context = FintraxSpringBootstrap.start(args)) {
                FintraxUI.launch(context, args);
            }
        } catch (RuntimeException exception) {
            StorageAlreadyInUseException storageException = findStorageAlreadyInUseException(exception);
            if (storageException == null) {
                throw exception;
            }

            Path storagePath = storageException.getStoragePath();
            log.error("Fintrax cannot start because storage is already in use at {}", storagePath, exception);
            StartupErrorApplication.showStorageInUse(storagePath);
            System.exit(1);
            return;
        }
        System.exit(0);
    }

    static StorageAlreadyInUseException findStorageAlreadyInUseException(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof StorageAlreadyInUseException storageException) {
                return storageException;
            }
        }
        return null;
    }
}
