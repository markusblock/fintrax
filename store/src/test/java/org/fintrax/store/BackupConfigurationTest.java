package org.fintrax.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupConfigurationTest {
    @TempDir
    Path tempDir;

    @Test
    void createsDefaultBackupLocationOutsideActiveStore() {
        Path storagePath = tempDir.resolve("data");
        Path defaultBackupPath = tempDir.resolve("backups");

        BackupConfiguration configuration = new BackupConfiguration(
                storagePath, tempDir.resolve("backup.properties"), defaultBackupPath, null);

        assertTrue(configuration.isAvailable());
        assertEquals(defaultBackupPath, configuration.destination());
        assertEquals(defaultBackupPath.resolve("snapshots"), configuration.snapshotsDirectory());
        assertTrue(Files.isDirectory(defaultBackupPath));
    }

    @Test
    void savesConfiguredDestinationOutsideStoreForNextStartup() {
        Path storagePath = tempDir.resolve("data");
        Path configFile = tempDir.resolve("backup.properties");
        Path nextDestination = tempDir.resolve("external-backups");

        BackupConfiguration configuration = new BackupConfiguration(
                storagePath, configFile, tempDir.resolve("backups"), null);
        configuration.saveDestination(nextDestination);

        assertEquals(tempDir.resolve("backups"), configuration.destination());

        BackupConfiguration reloaded = new BackupConfiguration(
                storagePath, configFile, tempDir.resolve("backups"), null);

        assertEquals(nextDestination, reloaded.destination());
        assertTrue(reloaded.isAvailable());
    }

    @Test
    void disablesBackupsWhenDestinationIsInsideActiveStore() {
        Path storagePath = tempDir.resolve("data");
        Path nestedBackupPath = storagePath.resolve("backups");

        BackupConfiguration configuration = new BackupConfiguration(
                storagePath, tempDir.resolve("backup.properties"), nestedBackupPath, null);

        assertFalse(configuration.isAvailable());
        assertTrue(configuration.startupWarning().isPresent());
        assertFalse(Files.exists(nestedBackupPath));
    }

    @Test
    void disablesBackupsWhenDestinationCannotBeCreated() throws Exception {
        Path blocker = tempDir.resolve("not-a-directory");
        Files.writeString(blocker, "file");

        BackupConfiguration configuration = new BackupConfiguration(
                tempDir.resolve("data"), tempDir.resolve("backup.properties"), blocker.resolve("backups"), null);

        assertFalse(configuration.isAvailable());
        assertTrue(configuration.startupWarning().isPresent());
    }

    @Test
    void disablesBackupsWhenExternalConfigurationCannotBeRead() throws Exception {
        Path configurationFile = Files.createDirectory(tempDir.resolve("backup.properties"));

        BackupConfiguration configuration = new BackupConfiguration(
                tempDir.resolve("data"), configurationFile, tempDir.resolve("backups"), null);

        assertFalse(configuration.isAvailable());
        assertTrue(configuration.startupWarning().isPresent());
        assertFalse(Files.exists(tempDir.resolve("backups")));
    }
}
