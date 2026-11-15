package org.fintrax.store;

import org.fintrax.model.AppSetting;
import org.fintrax.model.BankAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoreBackupIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void fullBackupRestoresDataIntoSeparateStoragePath() throws IOException {
        Path activePath = tempDir.resolve("active");
        Path continuousBackupPath = tempDir.resolve("backups").resolve("continuous");
        Path snapshotPath = tempDir.resolve("snapshots").resolve("snapshot-1");
        StoreManager activeStore = new StoreManager(activePath, continuousBackupPath);
        StoreManager restoredStore = null;
        try {
            activeStore.getRoot().getSettings().put("backup-test", AppSetting.builder()
                    .key("backup-test").value("snapshot-value").build());
            activeStore.store(activeStore.getRoot().getSettings());
            activeStore.getRoot().getAccounts().add(BankAccount.builder().id(7L).bankName("Existing bank").build());
            activeStore.store(activeStore.getRoot().getAccounts());

            activeStore.createFullBackup(snapshotPath);
            Path restoredPath = tempDir.resolve("restore-test");
            copyDirectory(snapshotPath, restoredPath);

            restoredStore = new StoreManager(restoredPath);
            assertEquals("snapshot-value", restoredStore.getRoot().getSettings().get("backup-test").getValue());
            assertEquals("Existing bank", restoredStore.getRoot().getAccounts().getFirst().getBankName());
        } finally {
            if (restoredStore != null) {
                restoredStore.shutdown();
            }
            activeStore.shutdown();
        }
    }

    @Test
    void continuousBackupRestoresExistingAndLatestDataIntoSeparateStoragePath() throws IOException {
        Path activePath = tempDir.resolve("active");
        Path continuousBackupPath = tempDir.resolve("backups").resolve("continuous");
        StoreManager initialStore = new StoreManager(activePath);
        initialStore.getRoot().getSettings().put("existing-data", AppSetting.builder()
                .key("existing-data").value("before-backup-enabled").build());
        initialStore.store(initialStore.getRoot().getSettings());
        initialStore.getRoot().getAccounts().add(BankAccount.builder().id(7L).bankName("Existing bank").build());
        initialStore.store(initialStore.getRoot().getAccounts());
        initialStore.shutdown();

        StoreManager activeStore = new StoreManager(activePath, continuousBackupPath);
        StoreManager restoredStore = null;
        try {
            activeStore.getRoot().getSettings().put("latest-data", AppSetting.builder()
                    .key("latest-data").value("after-backup-enabled").build());
            activeStore.store(activeStore.getRoot().getSettings());
            activeStore.shutdown();
            Path restoredPath = tempDir.resolve("continuous-restore-test");
            copyDirectory(continuousBackupPath, restoredPath);

            restoredStore = new StoreManager(restoredPath);
            assertEquals("before-backup-enabled",
                    restoredStore.getRoot().getSettings().get("existing-data").getValue());
            assertEquals("after-backup-enabled",
                    restoredStore.getRoot().getSettings().get("latest-data").getValue());
            assertEquals("Existing bank", restoredStore.getRoot().getAccounts().getFirst().getBankName());
        } finally {
            if (restoredStore != null) {
                restoredStore.shutdown();
            }
            if (!activeStore.isShutdown()) {
                activeStore.shutdown();
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path sourcePath : paths.toList()) {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath);
                }
            }
        }
    }
}
