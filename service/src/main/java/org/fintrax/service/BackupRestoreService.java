package org.fintrax.service;

import org.fintrax.store.StoreManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Validates full backups on a separate path before replacing active storage. */
public final class BackupRestoreService {
    private static final Pattern SNAPSHOT_NAME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}-\\d{3}-[0-9a-fA-F-]{36}");
    private static final Clock SYSTEM_CLOCK = Clock.systemDefaultZone();
    private static final DateTimeFormatter PRESERVED_STORAGE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    /** Lists completed full backups, newest first. */
    public List<Path> listFullBackups(Path snapshotsDirectory) throws IOException {
        if (!Files.isDirectory(snapshotsDirectory)) {
            return List.of();
        }
        try (Stream<Path> snapshots = Files.list(snapshotsDirectory)) {
            return snapshots.filter(Files::isDirectory)
                    .filter(path -> SNAPSHOT_NAME.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
    }

    /** Restores a completed full backup through a separate staging path, preserving active storage. */
    public RestoreResult restoreFullBackup(Path selectedBackup, Path snapshotsDirectory,
                                           Path activeStoragePath) throws IOException {
        return restoreFullBackup(selectedBackup, snapshotsDirectory, activeStoragePath, null);
    }

    /** Also archives the old continuous mirror so the restored storage can seed a fresh one. */
    public RestoreResult restoreFullBackup(Path selectedBackup, Path snapshotsDirectory,
                                           Path activeStoragePath, Path continuousBackupPath) throws IOException {
        Path backup = validateBackup(selectedBackup, snapshotsDirectory, activeStoragePath);
        RestorePaths paths = createRestorePaths(activeStoragePath, continuousBackupPath);
        try {
            copyBackup(backup, paths.stagingPath());
            validateRestoredStorage(paths.stagingPath());
            return promoteRestoredStorage(paths);
        } catch (IOException | RuntimeException failure) {
            deleteStagingPath(paths.stagingPath(), failure);
            throw failure;
        }
    }

    private Path validateBackup(Path selectedBackup, Path snapshotsDirectory, Path activeStoragePath)
            throws IOException {
        Path snapshotsRoot = snapshotsDirectory.toRealPath();
        Path backup = selectedBackup.toRealPath();
        if (!backup.getParent().equals(snapshotsRoot)
                || !SNAPSHOT_NAME.matcher(backup.getFileName().toString()).matches()) {
            throw new IllegalArgumentException("Select a completed full backup from " + snapshotsRoot);
        }

        Path activePath = activeStoragePath.toAbsolutePath().normalize();
        if (backup.startsWith(activePath) || activePath.startsWith(backup)) {
            throw new IllegalArgumentException("Backup and active storage paths must be separate");
        }
        return backup;
    }

    private RestorePaths createRestorePaths(Path activeStoragePath, Path continuousBackupPath) throws IOException {
        Path activePath = activeStoragePath.toAbsolutePath().normalize();
        Path parent = activePath.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Active storage must have a parent directory");
        }
        Files.createDirectories(parent);

        String activeName = activePath.getFileName().toString();
        Path stagingPath = parent.resolve(activeName + ".restore-" + UUID.randomUUID());
        Path preservedPath = parent.resolve(activeName + ".corrupt-"
                + PRESERVED_STORAGE_TIMESTAMP.format(LocalDateTime.now(SYSTEM_CLOCK)) + "-" + UUID.randomUUID());
        Path preservedContinuousPath = continuousBackupPath == null ? null
                : continuousBackupPath.resolveSibling(continuousBackupPath.getFileName()
                        + ".pre-restore-" + UUID.randomUUID());
        return new RestorePaths(activePath, stagingPath, preservedPath,
                continuousBackupPath, preservedContinuousPath);
    }

    private RestoreResult promoteRestoredStorage(RestorePaths paths) throws IOException {
        boolean activeStorageMoved = false;
        boolean continuousBackupMoved = false;
        try {
            if (Files.exists(paths.activePath(), LinkOption.NOFOLLOW_LINKS)) {
                move(paths.activePath(), paths.preservedPath());
                activeStorageMoved = true;
            }
            if (paths.continuousBackupPath() != null
                    && Files.exists(paths.continuousBackupPath(), LinkOption.NOFOLLOW_LINKS)) {
                move(paths.continuousBackupPath(), paths.preservedContinuousPath());
                continuousBackupMoved = true;
            }
            move(paths.stagingPath(), paths.activePath());
            return new RestoreResult(paths.activePath(),
                    activeStorageMoved ? Optional.of(paths.preservedPath()) : Optional.empty(),
                    continuousBackupMoved ? Optional.of(paths.preservedContinuousPath()) : Optional.empty());
        } catch (IOException | RuntimeException failure) {
            rollbackMove(paths.continuousBackupPath(), paths.preservedContinuousPath(),
                    continuousBackupMoved, failure);
            rollbackMove(paths.activePath(), paths.preservedPath(), activeStorageMoved, failure);
            throw failure;
        }
    }

    private void rollbackMove(Path originalPath, Path preservedPath, boolean moved, Throwable failure) {
        if (!moved || Files.exists(originalPath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            move(preservedPath, originalPath);
        } catch (IOException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void copyBackup(Path backup, Path stagingPath) throws IOException {
        try (Stream<Path> entries = Files.walk(backup)) {
            List<Path> files = entries.toList();
            if (files.size() <= 1) {
                throw new IOException("Full backup is empty: " + backup);
            }
            for (Path source : files) {
                if (Files.isSymbolicLink(source)) {
                    throw new IOException("Full backup contains a symbolic link: " + source);
                }
                Path target = stagingPath.resolve(backup.relativize(source));
                if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, LinkOption.NOFOLLOW_LINKS);
                }
            }
        }
    }

    private void validateRestoredStorage(Path stagingPath) {
        StoreManager restoredStore = new StoreManager(stagingPath);
        try {
            if (restoredStore.getRoot() == null) {
                throw new IllegalStateException("Restored storage has no data root");
            }
        } finally {
            restoredStore.shutdown();
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            Files.move(source, target);
        }
    }

    private void deleteStagingPath(Path stagingPath, Throwable failure) {
        if (!Files.exists(stagingPath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(stagingPath)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    public record RestoreResult(Path activeStoragePath, Optional<Path> preservedStoragePath,
                                Optional<Path> preservedContinuousBackupPath) {
    }

    private record RestorePaths(Path activePath, Path stagingPath, Path preservedPath,
                                Path continuousBackupPath, Path preservedContinuousPath) {
    }
}
