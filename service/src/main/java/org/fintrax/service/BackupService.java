package org.fintrax.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.store.FintraxProperties;
import org.fintrax.store.StoreManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class BackupService implements AutoCloseable {
    private static final int RETAINED_SNAPSHOT_COUNT = 30;
    private static final long STORAGE_HEALTH_CHECK_SECONDS = 2;
    private static final long SHUTDOWN_AWAIT_SECONDS = 30;
    private static final Pattern SNAPSHOT_NAME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}-\\d{3}-[0-9a-fA-F-]{36}");
    private static final DateTimeFormatter SNAPSHOT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss-SSS");

    private final StoreManager storeManager;
    private final FintraxProperties configuration;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private Consumer<Throwable> failureHandler = ignored -> { };
    private CompletableFuture<Path> startupBackup;
    private ScheduledFuture<?> storageHealthMonitor;

    /** Creates the startup backup coordinator using the system's local clock. */
    @Autowired
    public BackupService(StoreManager storeManager, FintraxProperties configuration) {
        this(storeManager, configuration, Clock.systemDefaultZone());
    }

    BackupService(StoreManager storeManager, FintraxProperties configuration, Clock clock) {
        this.storeManager = storeManager;
        this.configuration = configuration;
        this.clock = clock;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "fintrax-full-backup");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Creates one complete point-in-time backup for each app launch. */
    public synchronized CompletableFuture<Path> start(Consumer<Throwable> failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler);
        if (startupBackup != null) {
            return startupBackup;
        }
        startupBackup = new CompletableFuture<>();
        executor.execute(() -> performStartupBackup(startupBackup));
        return startupBackup;
    }

    private void performStartupBackup(CompletableFuture<Path> result) {
        try {
            if (!configuration.isAvailable()) {
                throw new IllegalStateException(configuration.startupWarning().orElse("Backup destination unavailable"));
            }
            Path snapshot = createSnapshot(LocalDateTime.now(clock));
            result.complete(snapshot);
            monitorStorageHealth();
        } catch (Exception exception) {
            configuration.disableForSession("Automatic backups stopped: " + exception.getMessage());
            result.completeExceptionally(exception);
            reportFailure(exception);
        }
    }

    private void reportFailure(Throwable failure) {
        try {
            failureHandler.accept(failure);
        } catch (RuntimeException callbackFailure) {
            log.warn("Backup failure handler failed", callbackFailure);
        }
    }

    private synchronized void monitorStorageHealth() {
        if (!configuration.isAvailable() || storageHealthMonitor != null || executor.isShutdown()) {
            return;
        }
        storageHealthMonitor = executor.scheduleWithFixedDelay(this::checkStorageHealth,
                STORAGE_HEALTH_CHECK_SECONDS, STORAGE_HEALTH_CHECK_SECONDS, TimeUnit.SECONDS);
    }

    private void checkStorageHealth() {
        if (!configuration.isAvailable()) {
            return;
        }
        try {
            if (storeManager.isAcceptingTasks()) {
                return;
            }
        } catch (RuntimeException exception) {
            disableBackupsAndReport(exception);
            return;
        }

        disableBackupsAndReport(new IllegalStateException(
                "Eclipse Store stopped accepting tasks; a backup or storage disruption may have occurred"));
    }

    private void disableBackupsAndReport(Throwable failure) {
        configuration.disableForSession("Storage backup unavailable: " + failure.getMessage());
        synchronized (this) {
            if (storageHealthMonitor != null) {
                storageHealthMonitor.cancel(false);
                storageHealthMonitor = null;
            }
        }
        reportFailure(failure);
    }

    private Path createSnapshot(LocalDateTime timestamp) {
        Path snapshotsDirectory = configuration.snapshotsDirectory();
        String snapshotName = SNAPSHOT_TIMESTAMP.format(timestamp) + "-" + UUID.randomUUID();
        Path incompleteSnapshot = snapshotsDirectory.resolve(".incomplete-" + UUID.randomUUID());
        Path completedSnapshot = snapshotsDirectory.resolve(snapshotName);

        try {
            removeIncompleteSnapshots(snapshotsDirectory);
            Files.createDirectories(snapshotsDirectory);
            Files.createDirectory(incompleteSnapshot);
            storeManager.createFullBackup(incompleteSnapshot);
            moveSnapshot(incompleteSnapshot, completedSnapshot);
            enforceRetention(snapshotsDirectory);
            log.info("Created local data backup at {}", completedSnapshot);
            return completedSnapshot;
        } catch (Exception exception) {
            log.error("Failed to create local data backup at {}", incompleteSnapshot, exception);
            deleteIncomplete(incompleteSnapshot);
            throw new CompletionException(exception);
        }
    }

    private void removeIncompleteSnapshots(Path snapshotsDirectory) {
        if (!Files.isDirectory(snapshotsDirectory)) {
            return;
        }
        try (Stream<Path> snapshots = Files.list(snapshotsDirectory)) {
            for (Path incompleteSnapshot : snapshots.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(".incomplete-"))
                    .toList()) {
                deleteRecursively(incompleteSnapshot);
            }
        } catch (IOException exception) {
            log.warn("Cannot remove incomplete local data backups at {}", snapshotsDirectory, exception);
        }
    }

    private void enforceRetention(Path snapshotsDirectory) {
        try (Stream<Path> snapshots = Files.list(snapshotsDirectory)) {
            List<Path> completedSnapshots = snapshots.filter(Files::isDirectory)
                    .filter(path -> SNAPSHOT_NAME.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
            for (Path expiredSnapshot : completedSnapshots.stream().skip(RETAINED_SNAPSHOT_COUNT).toList()) {
                deleteRecursively(expiredSnapshot);
            }
        } catch (IOException exception) {
            log.warn("Cannot apply local data backup retention at {}", snapshotsDirectory, exception);
        }
    }

    private void moveSnapshot(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            Files.move(source, target);
        }
    }

    private void deleteIncomplete(Path directory) {
        try {
            deleteRecursively(directory);
        } catch (IOException cleanupFailure) {
            log.warn("Cannot remove incomplete local data backup at {}", directory, cleanupFailure);
        }
    }

    private void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (storageHealthMonitor != null) {
                storageHealthMonitor.cancel(false);
                storageHealthMonitor = null;
            }
            executor.shutdown();
        }
        boolean interrupted = false;
        while (!executor.isTerminated()) {
            if (Thread.interrupted()) {
                interrupted = true;
            }
            try {
                executor.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                interrupted = true;
                Thread.currentThread().interrupt();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
