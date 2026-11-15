package org.fintrax.service;

import org.fintrax.model.AppSetting;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRestoreServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void restoresAndValidatesBackupBeforePreservingDamagedStorage() throws Exception {
        Path activePath = tempDir.resolve("data");
        Path snapshotsDirectory = tempDir.resolve("backups").resolve("snapshots");
        Path continuousBackupPath = tempDir.resolve("backups").resolve("continuous");
        Path snapshot = snapshotsDirectory.resolve(snapshotName());
        StoreManager sourceStore = new StoreManager(activePath);
        sourceStore.getRoot().getSettings().put("restore-test", AppSetting.builder()
                .key("restore-test").value("known-good").build());
        sourceStore.store(sourceStore.getRoot().getSettings());
        sourceStore.createFullBackup(snapshot);
        sourceStore.shutdown();

        Path marker = Files.writeString(activePath.resolve("damage.marker"), "preserve-me");
        Files.createDirectories(continuousBackupPath);
        Path continuousMarker = Files.writeString(continuousBackupPath.resolve("mirror.marker"), "old-mirror");
        BackupRestoreService restoreService = new BackupRestoreService();

        BackupRestoreService.RestoreResult result =
                restoreService.restoreFullBackup(snapshot, snapshotsDirectory, activePath, continuousBackupPath);

        assertEquals(activePath, result.activeStoragePath());
        assertTrue(result.preservedStoragePath().isPresent());
        assertEquals("preserve-me", Files.readString(result.preservedStoragePath().orElseThrow()
                .resolve(marker.getFileName())));
        assertTrue(result.preservedContinuousBackupPath().isPresent());
        assertEquals("old-mirror", Files.readString(result.preservedContinuousBackupPath().orElseThrow()
                .resolve(continuousMarker.getFileName())));
        assertFalse(Files.exists(continuousBackupPath));
        assertTrue(Files.isDirectory(snapshot));

        StoreManager restoredStore = new StoreManager(activePath, continuousBackupPath);
        try {
            assertEquals("known-good", restoredStore.getRoot().getSettings().get("restore-test").getValue());
        } finally {
            restoredStore.shutdown();
        }
    }

    @Test
    void rejectsInvalidBackupBeforeChangingActiveStorage() throws Exception {
        Path activePath = Files.createDirectory(tempDir.resolve("data"));
        Path marker = Files.writeString(activePath.resolve("damage.marker"), "preserve-me");
        Path snapshotsDirectory = Files.createDirectory(tempDir.resolve("snapshots"));
        Path emptySnapshot = Files.createDirectory(snapshotsDirectory.resolve(snapshotName()));
        Files.createDirectory(emptySnapshot.resolve("channel_0"));

        assertThrows(IOException.class, () -> new BackupRestoreService()
                .restoreFullBackup(emptySnapshot, snapshotsDirectory, activePath));

        assertEquals("preserve-me", Files.readString(marker));
    }

    private String snapshotName() {
        return "2026-11-15T10-00-00-000-" + UUID.randomUUID();
    }
}
