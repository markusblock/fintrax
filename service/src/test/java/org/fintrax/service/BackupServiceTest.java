package org.fintrax.service;

import org.fintrax.store.FintraxProperties;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackupServiceTest {
    @TempDir
    Path tempDir;

    private StoreManager storeManager;
    private BackupService backupService;

    @AfterEach
    void tearDown() throws Exception {
        if (backupService != null) {
            backupService.close();
        }
        if (storeManager != null && !storeManager.isShutdown()) {
            storeManager.shutdown();
        }
        try (var paths = Files.walk(tempDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void createsOneFullBackupOnEveryAppLaunch() throws Exception {
        Path storagePath = tempDir.resolve("active");
        Path backupPath = tempDir.resolve("backups");
        storeManager = new StoreManager(storagePath);
        FintraxProperties configuration = configuration(storagePath, backupPath);

        backupService = new BackupService(storeManager, configuration, fixedClock());
        backupService.start(ignored -> { }).get();
        backupService.close();

        backupService = new BackupService(storeManager, configuration, fixedClock());
        backupService.start(ignored -> { }).get();

        assertEquals(2, completedSnapshots(backupPath).size());
    }

    @Test
    void retainsOnlyThirtyNewestCompletedBackups() throws Exception {
        Path storagePath = tempDir.resolve("active");
        Path backupPath = tempDir.resolve("backups");
        Path snapshots = backupPath.resolve("snapshots");
        Files.createDirectories(snapshots);
        for (int day = 1; day <= 31; day++) {
            Files.createDirectory(snapshots.resolve(snapshotName(String.format("2026-10-%02dT00-00-00-000", day), day)));
        }
        storeManager = new StoreManager(storagePath);
        backupService = new BackupService(storeManager, configuration(storagePath, backupPath), fixedClock());

        backupService.start(ignored -> { }).get();

        assertEquals(30, completedSnapshots(backupPath).size());
        assertFalse(Files.exists(snapshots.resolve(snapshotName("2026-10-01T00-00-00-000", 1))));
    }

    @Test
    void failedSnapshotLeavesPreviousCompletedBackupUntouched() throws Exception {
        Path storagePath = tempDir.resolve("active");
        Path backupPath = tempDir.resolve("backups");
        Path previousSnapshot = backupPath.resolve("snapshots")
                .resolve(snapshotName("2026-11-14T10-00-00-000", 14));
        Files.createDirectories(previousSnapshot);
        Path marker = Files.writeString(previousSnapshot.resolve("marker"), "known-good");
        storeManager = new StoreManager(storagePath);
        storeManager.shutdown();
        backupService = new BackupService(storeManager, configuration(storagePath, backupPath), fixedClock());

        assertThrows(ExecutionException.class, () -> backupService.start(ignored -> { }).get());

        assertEquals("known-good", Files.readString(marker));
        assertEquals(1, completedSnapshots(backupPath).size());
    }

    @Test
    void reportsUnavailableDestinationAtStartup() throws Exception {
        Path storagePath = tempDir.resolve("active");
        storeManager = new StoreManager(storagePath);
        FintraxProperties configuration = configuration(storagePath, storagePath.resolve("backups"));
        backupService = new BackupService(storeManager, configuration, fixedClock());
        CompletableFuture<Throwable> reportedFailure = new CompletableFuture<>();

        assertThrows(ExecutionException.class,
                () -> backupService.start(reportedFailure::complete).get());

        assertNotNull(reportedFailure.get());
        assertFalse(configuration.isAvailable());
    }

    @Test
    void removesIncompleteBackupLeftByAnEarlierLaunch() throws Exception {
        Path storagePath = tempDir.resolve("active");
        Path backupPath = tempDir.resolve("backups");
        Path incompleteSnapshot = backupPath.resolve("snapshots").resolve(".incomplete-previous-run");
        Files.createDirectories(incompleteSnapshot);
        Files.writeString(incompleteSnapshot.resolve("partial"), "unfinished");
        storeManager = new StoreManager(storagePath);
        backupService = new BackupService(storeManager, configuration(storagePath, backupPath), fixedClock());

        backupService.start(ignored -> { }).get();

        assertFalse(Files.exists(incompleteSnapshot));
        assertEquals(1, completedSnapshots(backupPath).size());
    }

    private FintraxProperties configuration(Path storagePath, Path backupPath) {
        return new FintraxProperties(tempDir.resolve("fintrax.properties"),
                storagePath.toString(), backupPath.toString());
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"));
    }

    private List<Path> completedSnapshots(Path backupPath) throws Exception {
        Path snapshots = backupPath.resolve("snapshots");
        if (!Files.isDirectory(snapshots)) {
            return List.of();
        }
        try (var paths = Files.list(snapshots)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith(".incomplete-"))
                    .toList();
        }
    }

    private String snapshotName(String timestamp, int suffix) {
        return timestamp + String.format("-00000000-0000-0000-0000-%012d", suffix);
    }
}
