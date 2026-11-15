package org.fintrax.store;

import java.nio.file.Path;

public final class StorageAlreadyInUseException extends IllegalStateException {
    private final Path storagePath;

    public StorageAlreadyInUseException(Path storagePath) {
        super("Storage is already in use: " + storagePath);
        this.storagePath = storagePath;
    }

    public Path getStoragePath() {
        return storagePath;
    }
}
