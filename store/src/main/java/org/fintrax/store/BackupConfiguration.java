package org.fintrax.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** Stores the backup destination outside the Eclipse Store it configures. */
public final class BackupConfiguration {
    private static final String DESTINATION_KEY = "destination";

    private final Path storagePath;
    private final Path configurationFile;
    private final Path destination;
    private String configurationWarning;
    private final String startupWarning;
    private volatile String sessionWarning;

    /** Loads the external backup preference and validates the destination before store startup. */
    public BackupConfiguration(Path storagePath, Path configurationFile,
                               Path defaultDestination, String destinationOverride) {
        this.storagePath = normalize(storagePath);
        this.configurationFile = normalize(configurationFile);
        this.destination = resolveDestination(defaultDestination, destinationOverride);
        this.startupWarning = configurationWarning != null
                ? configurationWarning : validateAtStartup(this.destination).orElse(null);
    }

    /** Returns the configured backup root used by the current launch. */
    public Path destination() {
        return destination;
    }

    /** Returns the directory used by Eclipse Store's continuously mirrored copy. */
    public Path continuousBackupDirectory() {
        return destination.resolve("continuous");
    }

    /** Returns the directory used for retained point-in-time backups. */
    public Path snapshotsDirectory() {
        return destination.resolve("snapshots");
    }

    /** Returns whether backup storage was available when the active store started. */
    public boolean isAvailable() {
        return startupWarning == null && sessionWarning == null;
    }

    /** Returns the reason backups were disabled for this launch, if any. */
    public Optional<String> startupWarning() {
        return Optional.ofNullable(sessionWarning == null ? startupWarning : sessionWarning);
    }

    /** Disables backups for the rest of this app session after startup setup fails. */
    public void disableForSession(String warning) {
        sessionWarning = warning;
    }

    /** Persists a validated destination for the next app launch. */
    public void saveDestination(Path selectedDestination) {
        Path normalizedDestination = normalize(selectedDestination);
        validateOverlap(normalizedDestination).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
        validateWritable(normalizedDestination).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });

        Properties properties = new Properties();
        properties.setProperty(DESTINATION_KEY, normalizedDestination.toString());
        Path parent = configurationFile.getParent();
        Path temporaryFile = null;
        try {
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, "backup-config-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                properties.store(output, "Fintrax backup configuration");
            }
            moveReplacing(temporaryFile, configurationFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save backup configuration: " + configurationFile, exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // The configuration was already saved, or the temporary file is best-effort cleanup.
                }
            }
        }
    }

    private Path resolveDestination(Path defaultDestination, String destinationOverride) {
        Properties properties = new Properties();
        if (Files.exists(configurationFile) && !Files.isRegularFile(configurationFile)) {
            configurationWarning = "Backup configuration is not a file: " + configurationFile;
            return normalize(defaultDestination);
        }
        if (Files.isRegularFile(configurationFile)) {
            try (InputStream input = Files.newInputStream(configurationFile)) {
                properties.load(input);
                String configuredDestination = properties.getProperty(DESTINATION_KEY);
                if (configuredDestination != null && !configuredDestination.isBlank()) {
                    return normalize(expandHome(configuredDestination));
                }
            } catch (IOException | IllegalArgumentException exception) {
                configurationWarning = "Cannot read backup configuration: " + configurationFile
                        + " (" + exception.getMessage() + ")";
                return normalize(defaultDestination);
            }
        }
        if (destinationOverride != null && !destinationOverride.isBlank()) {
            try {
                return normalize(expandHome(destinationOverride));
            } catch (IllegalArgumentException exception) {
                configurationWarning = "Invalid backup destination: " + destinationOverride;
                return normalize(defaultDestination);
            }
        }
        return normalize(defaultDestination);
    }

    private Optional<String> validateAtStartup(Path candidate) {
        Optional<String> overlap = validateOverlap(candidate);
        if (overlap.isPresent()) {
            return overlap;
        }
        return validateWritable(candidate);
    }

    private Optional<String> validateOverlap(Path candidate) {
        if (candidate.startsWith(storagePath) || storagePath.startsWith(candidate)) {
            return Optional.of("Backup destination must not overlap active storage: " + candidate);
        }
        try {
            Path realStoragePath = resolveThroughExistingAncestor(storagePath);
            Path realDestination = resolveThroughExistingAncestor(candidate);
            if (realDestination.startsWith(realStoragePath) || realStoragePath.startsWith(realDestination)) {
                return Optional.of("Backup destination must not overlap active storage: " + candidate);
            }
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.of("Cannot verify backup destination against active storage: " + candidate);
        }
    }

    private Path resolveThroughExistingAncestor(Path path) throws IOException {
        Path existingAncestor = path;
        while (existingAncestor != null && !Files.exists(existingAncestor)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null) {
            return path;
        }
        Path realAncestor = existingAncestor.toRealPath();
        return realAncestor.resolve(existingAncestor.relativize(path)).normalize();
    }

    private Optional<String> validateWritable(Path candidate) {
        Path probe = null;
        try {
            Files.createDirectories(candidate);
            probe = Files.createTempFile(candidate, ".fintrax-backup-check-", ".tmp");
            return Optional.empty();
        } catch (IOException | SecurityException exception) {
            return Optional.of("Backup destination is unavailable: " + candidate + " (" + exception.getMessage() + ")");
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException ignored) {
                    // A failed probe cleanup does not make the destination unavailable.
                }
            }
        }
    }

    private Path normalize(Path path) {
        return expandHome(path.toString()).toAbsolutePath().normalize();
    }

    private Path expandHome(String path) {
        if (path.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home")).resolve(path.substring(2));
        }
        return Path.of(path);
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveFailure) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
