package org.fintrax.app;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.store.storage.exceptions.StorageException;
import org.springframework.context.ConfigurableApplicationContext;
import org.fintrax.store.FintraxProperties;
import org.fintrax.store.StorageAlreadyInUseException;
import org.fintrax.store.StoragePathResolver;
import org.fintrax.ui.FintraxUI;

import java.nio.file.Path;

@Slf4j
public final class FintraxApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context;
        try {
            context = FintraxSpringBootstrap.start(args);
        } catch (RuntimeException exception) {
            StorageAlreadyInUseException storageInUse = findStorageAlreadyInUseException(exception);
            if (storageInUse != null) {
                Path storagePath = storageInUse.getStoragePath();
                log.error("Fintrax cannot start because storage is already in use at {}", storagePath, exception);
                StartupErrorApplication.showStorageInUse(storagePath);
                System.exit(1);
                return;
            }

            StorageException storageFailure = findStorageException(exception);
            if (storageFailure == null) {
                throw exception;
            }

            FintraxProperties fintraxProperties = resolveFintraxProperties(args);
            Path storagePath = StoragePathResolver.resolve(fintraxProperties.storagePath().toString());
            log.error("Fintrax cannot open storage at {}", storagePath, exception);
            boolean restored = StartupErrorApplication.showStorageFailure(
                    storagePath, fintraxProperties, storageFailure, args);
            System.exit(restored ? 0 : 1);
            return;
        }

        try (context) {
            FintraxUI.launch(context, args);
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

    static StorageException findStorageException(Throwable exception) {
        StorageException storageFailure = null;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof StorageException storageException) {
                storageFailure = storageException;
            }
        }
        return storageFailure;
    }

    private static FintraxProperties resolveFintraxProperties(String[] args) {
        String configurationFile = optionValue(args, "fintrax.config.path", FintraxProperties.DEFAULT_FILE);
        String storageOverride = optionValue(args, "fintrax.storage.path", null);
        String backupOverride = optionValue(args, "fintrax.backup.path", null);
        return new FintraxProperties(Path.of(configurationFile), storageOverride, backupOverride);
    }

    private static String optionValue(String[] args, String option, String defaultValue) {
        String prefix = "--" + option + "=";
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (argument.startsWith(prefix)) {
                String value = argument.substring(prefix.length());
                if (!value.isBlank()) {
                    return value;
                }
            }
            if (argument.equals("--" + option) && index + 1 < args.length && !args[index + 1].isBlank()) {
                return args[index + 1];
            }
        }
        String systemProperty = System.getProperty(option);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }
        return defaultValue;
    }
}
