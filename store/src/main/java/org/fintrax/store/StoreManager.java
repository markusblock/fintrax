package org.fintrax.store;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.fintrax.model.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class StoreManager {
    private static final int MAX_ACTIVITY_LOGS = 10_000;
    private final EmbeddedStorageManager storage;
    private final DataRoot root;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Long, BankAccount> accountIndex = new ConcurrentHashMap<>();
    private final Map<Long, Transaction> transactionIndex = new ConcurrentHashMap<>();
    private final Map<Long, Category> categoryIndex = new ConcurrentHashMap<>();
    private final Map<Long, Label> labelIndex = new ConcurrentHashMap<>();
    private final Map<Long, Rule> ruleIndex = new ConcurrentHashMap<>();

    public StoreManager(Path storagePath) {
        log.info("Initializing StoreManager at {}", storagePath);
        storage = EmbeddedStorage.start(storagePath);
        root = storage.ensureRoot(DataRoot::new);
        log.info("DataRoot initialized");

        new CategorySeeder().seed(root);
        if (!root.getCategories().isEmpty()) {
            storage.store(root.getCategories());
        }

        rebuildIndexes();
    }

    private void rebuildIndexes() {
        lock.writeLock().lock();
        try {
            accountIndex.clear();
            root.getAccounts().forEach(a -> accountIndex.put(a.getId(), a));

            transactionIndex.clear();
            root.getTransactions().forEach(t -> transactionIndex.put(t.getId(), t));

            categoryIndex.clear();
            root.getCategories().forEach(c -> categoryIndex.put(c.getId(), c));

            labelIndex.clear();
            root.getLabels().forEach(l -> labelIndex.put(l.getId(), l));

            ruleIndex.clear();
            root.getRules().forEach(r -> ruleIndex.put(r.getId(), r));

            log.debug("Rebuilt indexes: {} accounts, {} transactions, {} categories, {} labels, {} rules",
                    accountIndex.size(), transactionIndex.size(), categoryIndex.size(),
                    labelIndex.size(), ruleIndex.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public DataRoot getRoot() {
        return root;
    }

    public void store(Object obj) {
        lock.writeLock().lock();
        try {
            storage.store(obj);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public BankAccount getAccount(Long id) {
        lock.readLock().lock();
        try {
            return root.getAccounts().stream()
                    .filter(a -> a.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Transaction getTransaction(Long id) {
        lock.readLock().lock();
        try {
            return root.getTransactions().stream()
                    .filter(t -> t.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Category getCategory(Long id) {
        lock.readLock().lock();
        try {
            return root.getCategories().stream()
                    .filter(c -> c.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Label getLabel(Long id) {
        lock.readLock().lock();
        try {
            return root.getLabels().stream()
                    .filter(l -> l.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Rule getRule(Long id) {
        lock.readLock().lock();
        try {
            return root.getRules().stream()
                    .filter(r -> r.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addActivityLog(ActivityLog log) {
        lock.writeLock().lock();
        try {
            root.getActivityLogs().add(log);
            if (root.getActivityLogs().size() > MAX_ACTIVITY_LOGS) {
                root.getActivityLogs().remove(0);
            }
            storage.store(root.getActivityLogs());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void shutdown() {
        log.info("Shutting down StoreManager");
        storage.shutdown();
    }
}
