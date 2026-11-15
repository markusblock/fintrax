package org.fintrax.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FintraxPropertiesTest {
    @TempDir
    Path tempDir;

    @Test
    void writesBothPathsToExternalFintraxPropertiesFile() throws Exception {
        Path storagePath = tempDir.resolve("data");
        Path backupPath = tempDir.resolve("backups");
        Path propertiesFile = tempDir.resolve("fintrax.properties");

        FintraxProperties properties = new FintraxProperties(
                propertiesFile, storagePath.toString(), backupPath.toString());

        Properties persisted = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            persisted.load(input);
        }
        assertEquals(storagePath.toString(), persisted.getProperty("fintrax.storage.path"));
        assertEquals(backupPath.toString(), persisted.getProperty("fintrax.backup.path"));
        assertEquals(backupPath.resolve("snapshots"), properties.snapshotsDirectory());
    }

    @Test
    void savesStorageAndBackupPathEditsTogetherForNextLaunch() {
        Path storagePath = tempDir.resolve("data");
        Path backupPath = tempDir.resolve("backups");
        Path propertiesFile = tempDir.resolve("fintrax.properties");
        FintraxProperties properties = new FintraxProperties(
                propertiesFile, storagePath.toString(), backupPath.toString());
        Path nextStoragePath = tempDir.resolve("next-data");
        Path nextBackupPath = tempDir.resolve("next-backups");

        properties.saveStoragePath(nextStoragePath);
        properties.saveDestination(nextBackupPath);

        assertEquals(storagePath, properties.storagePath());
        assertEquals(backupPath, properties.destination());
        assertEquals(nextStoragePath, properties.configuredStoragePath());
        assertEquals(nextBackupPath, properties.configuredDestination());

        FintraxProperties reloaded = new FintraxProperties(propertiesFile, null, null);
        assertEquals(nextStoragePath, reloaded.storagePath());
        assertEquals(nextBackupPath, reloaded.destination());
    }

    @Test
    void migratesLegacyBackupPropertiesAndAddsStoragePath() throws Exception {
        Path propertiesFile = tempDir.resolve("fintrax.properties");
        Path legacyFile = tempDir.resolve("backup.properties");
        Path legacyBackupPath = tempDir.resolve("legacy-backups");
        Properties legacy = new Properties();
        legacy.setProperty("destination", legacyBackupPath.toString());
        try (OutputStream output = Files.newOutputStream(legacyFile)) {
            legacy.store(output, "legacy backup config");
        }

        FintraxProperties properties = new FintraxProperties(
                propertiesFile, tempDir.resolve("data"), tempDir.resolve("backups"), null, null);
        Properties migrated = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            migrated.load(input);
        }

        assertEquals(tempDir.resolve("data").toString(), migrated.getProperty("fintrax.storage.path"));
        assertEquals(legacyBackupPath.toString(), migrated.getProperty("fintrax.backup.path"));
        assertFalse(Files.exists(legacyFile));
        assertEquals(legacyBackupPath, properties.destination());
    }

    @Test
    void disablesBackupsWhenLocationsOverlap() {
        Path storagePath = tempDir.resolve("data");
        Path propertiesFile = tempDir.resolve("fintrax.properties");
        FintraxProperties properties = new FintraxProperties(
                propertiesFile, storagePath.toString(), storagePath.resolve("backups").toString());

        assertFalse(properties.isAvailable());
        assertTrue(properties.startupWarning().isPresent());
        assertFalse(Files.exists(storagePath.resolve("backups")));
    }

    @Test
    void reportsUnreadableExternalConfiguration() throws Exception {
        Path propertiesFile = Files.createDirectory(tempDir.resolve("fintrax.properties"));

        Path backupPath = tempDir.resolve("backups");
        FintraxProperties properties = new FintraxProperties(
                propertiesFile, tempDir.resolve("data"), backupPath, null, null);

        assertFalse(properties.isAvailable());
        assertTrue(properties.startupWarning().isPresent());
        assertFalse(Files.exists(backupPath));
    }
}
