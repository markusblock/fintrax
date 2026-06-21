package org.fintrax.store;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class StoragePathResolver {
    private static final String DEFAULT_PATH = "~/.fintrax/data";
    private static final String SYS_PROP = "fintrax.storage.path";

    public static Path resolve() {
        String override = System.getProperty(SYS_PROP);
        if (override != null && !override.isBlank()) {
            return resolve(override);
        }
        return resolve(DEFAULT_PATH);
    }

    public static Path resolve(String path) {
        if (path.startsWith("~")) {
            path = System.getProperty("user.home") + path.substring(1);
        }

        Path resolved = Paths.get(path);

        if (!Files.exists(resolved)) {
            try {
                Files.createDirectories(resolved);
                log.info("Created storage directory: {}", resolved);
            } catch (Exception e) {
                log.error("Failed to create storage directory: {}", resolved, e);
                throw new RuntimeException("Cannot create storage directory: " + resolved, e);
            }
        }

        return resolved;
    }
}
