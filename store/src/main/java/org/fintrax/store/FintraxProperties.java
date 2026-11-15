package org.fintrax.store;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/** Stores Fintrax storage and backup locations outside the Eclipse Store data directory. */
@Slf4j
public final class FintraxProperties {
    public static final String DEFAULT_FILE = "~/.fintrax/fintrax.properties";

    private static final String STORAGE_PATH_KEY = "fintrax.storage.path";
    private static final String BACKUP_PATH_KEY = "fintrax.backup.path";
    private static final String LEGACY_BACKUP_PATH_KEY = "destination";
    private static final String LOCATION_SEPARATOR = " and ";

    private final Path propertiesFile;
    private final Path legacyPropertiesFile;
    private final Path storagePath;
    private final Path destination;
    private final AtomicReference<Path> configuredStoragePath = new AtomicReference<>();
    private final AtomicReference<Path> configuredDestination = new AtomicReference<>();
    private String configurationWarning;
    private final String startupWarning;
    private volatile String sessionWarning;
    private boolean removeLegacyBackupConfig;

    public FintraxProperties(Path propertiesFile, String storageOverride, String backupOverride) {
        this(propertiesFile, defaultStoragePath(), defaultBackupPath(),
                storageOverride, backupOverride);
    }

    FintraxProperties(Path propertiesFile, Path defaultStoragePath, Path defaultBackupPath,
                       String storageOverride, String backupOverride) {
        this.propertiesFile = normalize(propertiesFile);
        this.legacyPropertiesFile = isDefaultPropertiesFile(this.propertiesFile)
                ? this.propertiesFile.resolveSibling("backup.properties") : null;
        Properties properties = loadProperties();
        this.storagePath = resolvePath(storageOverride, properties.getProperty(STORAGE_PATH_KEY),
                defaultStoragePath, "storage");
        this.destination = resolvePath(backupOverride, properties.getProperty(BACKUP_PATH_KEY),
                defaultBackupPath, "backup");
        this.configuredStoragePath.set(storagePath);
        this.configuredDestination.set(destination);

        String warning = configurationWarning;
        if (warning == null) {
            warning = validateBackupDestination(destination).orElse(null);
        }
        if (configurationWarning == null) {
            boolean writeDefaults = removeLegacyBackupConfig;
            if (properties.getProperty(STORAGE_PATH_KEY) == null
                    || properties.getProperty(STORAGE_PATH_KEY).isBlank()) {
                properties.setProperty(STORAGE_PATH_KEY, storagePath.toString());
                writeDefaults = true;
            }
            if (properties.getProperty(BACKUP_PATH_KEY) == null
                    || properties.getProperty(BACKUP_PATH_KEY).isBlank()) {
                properties.setProperty(BACKUP_PATH_KEY, destination.toString());
                writeDefaults = true;
            }
            if (writeDefaults) {
                try {
                    persist(properties);
                    removeMigratedLegacyFile();
                } catch (IOException exception) {
                    warning = "Cannot save Fintrax configuration: " + propertiesFile
                            + " (" + exception.getMessage() + ")";
                }
            }
        }
        this.startupWarning = warning;
    }

    /** Returns the storage directory used by the current launch. */
    public Path storagePath() {
        return storagePath;
    }

    /** Returns the storage directory currently configured for the next launch. */
    public Path configuredStoragePath() {
        return configuredStoragePath.get();
    }

    /** Returns the backup root used by the current launch. */
    public Path destination() {
        return destination;
    }

    /** Returns the backup directory currently configured for the next launch. */
    public Path configuredDestination() {
        return configuredDestination.get();
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

    /** Persists a backup destination for the next app launch. */
    public synchronized void saveDestination(Path selectedDestination) {
        Path normalizedDestination = normalize(selectedDestination);
        validateOverlap(configuredStoragePath.get(), normalizedDestination).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
        savePath(BACKUP_PATH_KEY, normalizedDestination);
    }

    /** Persists a storage directory for the next app launch. */
    public synchronized void saveStoragePath(Path selectedStoragePath) {
        Path normalizedStoragePath = normalize(selectedStoragePath);
        validateOverlap(normalizedStoragePath, configuredDestination.get()).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
        validateWritable(normalizedStoragePath).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
        savePath(STORAGE_PATH_KEY, normalizedStoragePath);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        if (Files.exists(propertiesFile) && !Files.isRegularFile(propertiesFile)) {
            configurationWarning = "Fintrax configuration is not a file: " + propertiesFile;
            return properties;
        }
        if (Files.isRegularFile(propertiesFile)) {
            try (InputStream input = Files.newInputStream(propertiesFile)) {
                properties.load(input);
            } catch (IOException | IllegalArgumentException exception) {
                configurationWarning = "Cannot read Fintrax configuration: " + propertiesFile
                        + " (" + exception.getMessage() + ")";
            }
        }
        migrateLegacyBackupProperty(properties);
        return properties;
    }

    private void migrateLegacyBackupProperty(Properties properties) {
        if (legacyPropertiesFile == null || !Files.exists(legacyPropertiesFile)) {
            return;
        }
        if (!isBlank(properties.getProperty(BACKUP_PATH_KEY)) && Files.isRegularFile(legacyPropertiesFile)) {
            removeLegacyBackupConfig = true;
            return;
        }
        if (!Files.isRegularFile(legacyPropertiesFile)) {
            configurationWarning = "Legacy backup configuration is not a file: " + legacyPropertiesFile;
            return;
        }
        Properties legacy = new Properties();
        try (InputStream input = Files.newInputStream(legacyPropertiesFile)) {
            legacy.load(input);
            String oldDestination = legacy.getProperty(LEGACY_BACKUP_PATH_KEY);
            if (!isBlank(oldDestination)) {
                properties.setProperty(BACKUP_PATH_KEY, oldDestination);
                removeLegacyBackupConfig = true;
            }
        } catch (IOException | IllegalArgumentException exception) {
            configurationWarning = "Cannot migrate legacy backup configuration: " + legacyPropertiesFile
                    + " (" + exception.getMessage() + ")";
        }
    }

    private void removeMigratedLegacyFile() {
        if (!removeLegacyBackupConfig) {
            return;
        }
        try {
            Files.deleteIfExists(legacyPropertiesFile);
        } catch (IOException exception) {
            log.warn("Migrated backup destination but could not remove legacy config {}", legacyPropertiesFile,
                    exception);
        }
    }

    private boolean isDefaultPropertiesFile(Path path) {
        return "fintrax.properties".equals(path.getFileName().toString());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Path resolvePath(String override, String configuredPath, Path defaultPath, String label) {
        String value = override != null && !override.isBlank() ? override : configuredPath;
        if (value == null || value.isBlank()) {
            return normalize(defaultPath);
        }
        try {
            return normalize(expandHome(value));
        } catch (IllegalArgumentException exception) {
            configurationWarning = "Invalid Fintrax " + label + " path: " + value;
            return normalize(defaultPath);
        }
    }

    private Optional<String> validateBackupDestination(Path candidate) {
        Optional<String> overlap = validateOverlap(storagePath, candidate);
        if (overlap.isPresent()) {
            return overlap;
        }
        return validateWritable(candidate);
    }

    private static Path defaultStoragePath() {
        return Path.of(System.getProperty("user.home"), ".fintrax", "data");
    }

    private static Path defaultBackupPath() {
        return Path.of(System.getProperty("user.home"), ".fintrax", "backups");
    }

    private Optional<String> validateOverlap(Path first, Path second) {
        if (first.startsWith(second) || second.startsWith(first)) {
            return Optional.of("Storage and backup locations must not overlap: " + first + LOCATION_SEPARATOR + second);
        }
        try {
            Path realFirst = resolveThroughExistingAncestor(first);
            Path realSecond = resolveThroughExistingAncestor(second);
            if (realFirst.startsWith(realSecond) || realSecond.startsWith(realFirst)) {
                return Optional.of("Storage and backup locations must not overlap: " + first
                        + LOCATION_SEPARATOR + second);
            }
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.of("Cannot verify storage and backup locations: " + first + LOCATION_SEPARATOR + second);
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
            probe = Files.createTempFile(candidate, ".fintrax-config-check-", ".tmp");
            return Optional.empty();
        } catch (IOException | SecurityException exception) {
            return Optional.of("Fintrax location is unavailable: " + candidate + " (" + exception.getMessage() + ")");
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException ignored) {
                    // A failed probe cleanup does not make the location unavailable.
                }
            }
        }
    }

    private void savePath(String key, Path value) {
        Properties properties = loadProperties();
        properties.setProperty(STORAGE_PATH_KEY, configuredStoragePath.get().toString());
        properties.setProperty(BACKUP_PATH_KEY, configuredDestination.get().toString());
        properties.setProperty(key, value.toString());
        try {
            persist(properties);
            if (STORAGE_PATH_KEY.equals(key)) {
                configuredStoragePath.set(value);
            } else if (BACKUP_PATH_KEY.equals(key)) {
                configuredDestination.set(value);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save Fintrax configuration: " + propertiesFile, exception);
        }
    }

    private void persist(Properties properties) throws IOException {
        Path parent = propertiesFile.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, "fintrax-config-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                properties.store(output, "Fintrax configuration");
            }
            moveReplacing(temporaryFile, propertiesFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
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
