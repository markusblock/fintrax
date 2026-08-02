package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.PinStorage;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class SyncService {
    private final StoreManager store;
    private final BankingProtocol bankingProtocol;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final ActivityLogger activityLogger;
    private final PinStorage pinStorage;
    private final RuleEngine ruleEngine;
    private final AtomicInteger activeSyncs = new AtomicInteger();
    private long nextId = 1;

    public SyncService(StoreManager store, BankingProtocol bankingProtocol,
                       TransactionService transactionService, AccountService accountService,
                       ActivityLogger activityLogger, PinStorage pinStorage, RuleEngine ruleEngine) {
        this.store = store;
        this.bankingProtocol = bankingProtocol;
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.activityLogger = activityLogger;
        this.pinStorage = pinStorage;
        this.ruleEngine = ruleEngine;
        initializeNextId();
    }

    private void initializeNextId() {
        nextId = store.getRoot().getSyncLogs().stream()
                .mapToLong(SyncLog::getId)
                .max()
                .orElse(0) + 1;
    }

    public SyncLog syncAccount(Long accountId, String pin) {
        activeSyncs.incrementAndGet();
        try {
            return syncAccountInternal(accountId, pin);
        } finally {
            activeSyncs.decrementAndGet();
        }
    }

    public boolean isSyncing() {
        return activeSyncs.get() > 0;
    }

    private SyncLog syncAccountInternal(Long accountId, String pin) {
        BankAccount account = store.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }

        SyncLog syncLog = SyncLog.builder()
                .id(nextId++)
                .bankAccountId(accountId)
                .startedAt(LocalDateTime.now())
                .status(SyncStatus.PARTIAL)
                .build();

        store.getRoot().getSyncLogs().add(syncLog);
        store.store(store.getRoot().getSyncLogs());

        try {
            List<Transaction> fetchedTransactions = bankingProtocol.fetchTransactions(account, pin);

            int newCount = 0;
            int skippedCount = 0;

            for (Transaction fetched : fetchedTransactions) {
                boolean exists = store.getRoot().getTransactions().stream()
                        .anyMatch(t -> t.getChecksum().equals(fetched.getChecksum()));

                if (!exists) {
                    transactionService.createTransaction(fetched);
                    ruleEngine.applyRules(fetched);
                    newCount++;
                } else {
                    skippedCount++;
                }
            }

            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setStatus(SyncStatus.SUCCESS);
            syncLog.setNewCount(newCount);
            syncLog.setSkippedCount(skippedCount);

            accountService.updateLastSync(accountId, LocalDateTime.now());

            store.store(syncLog);

            activityLogger.log(ActivityAction.SYNC, EntityType.ACCOUNT, accountId,
                    String.format("Synced account: %d new, %d skipped", newCount, skippedCount));

            log.info("Synced account {}: {} new, {} skipped", accountId, newCount, skippedCount);

        } catch (BankingException e) {
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage());
            store.store(syncLog);

            activityLogger.log(ActivityAction.SYNC, EntityType.ACCOUNT, accountId,
                    "Sync failed: " + e.getMessage());

            log.error("Sync failed for account {}", accountId, e);
        }

        return syncLog;
    }

    public List<SyncLog> getSyncLogs(Long accountId) {
        return store.getRoot().getSyncLogs().stream()
                .filter(l -> l.getBankAccountId().equals(accountId))
                .toList();
    }
}
