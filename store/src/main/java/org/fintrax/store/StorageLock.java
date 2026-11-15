package org.fintrax.store;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Holds an operating-system lock for the lifetime of one storage manager. */
final class StorageLock implements AutoCloseable {
    private static final String LOCK_FILE_NAME = ".fintrax.lock";

    private final Path storagePath;
    private final FileChannel channel;
    private final FileLock lock;

    private StorageLock(Path storagePath, FileChannel channel, FileLock lock) {
        this.storagePath = storagePath;
        this.channel = channel;
        this.lock = lock;
    }

    static StorageLock acquire(Path storagePath) {
        Path canonicalPath;
        try {
            Files.createDirectories(storagePath);
            canonicalPath = storagePath.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot access storage at " + storagePath, exception);
        }

        FileChannel channel = null;
        try {
            // Keep the sidecar in place: deleting it can let concurrent openers lock different inodes.
            channel = FileChannel.open(canonicalPath.resolve(LOCK_FILE_NAME),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                lock = null;
            }
            if (lock == null) {
                throw new StorageAlreadyInUseException(canonicalPath);
            }
            return new StorageLock(canonicalPath, channel, lock);
        } catch (StorageAlreadyInUseException exception) {
            closeChannel(channel, exception);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            closeChannel(channel, exception);
            throw new IllegalStateException("Cannot acquire storage lock at " + canonicalPath, exception);
        } catch (Error error) {
            closeChannel(channel, error);
            throw error;
        }
    }

    Path storagePath() {
        return storagePath;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            lock.release();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeChannel(FileChannel channel, Throwable failure) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
    }
}
