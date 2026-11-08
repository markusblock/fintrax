package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.PinStorage;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class SyncService {
    private final StoreManager store;
    private final BankingProtocol bankingProtocol;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final ActivityLogger activityLogger;
    private final AtomicInteger activeSyncs = new AtomicInteger();
    private long nextId = 1;

    @Autowired
    public SyncService(StoreManager store, BankingProtocol bankingProtocol,
                       TransactionService transactionService, AccountService accountService,
                       ActivityLogger activityLogger) {
        this.store = store;
        this.bankingProtocol = bankingProtocol;
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.activityLogger = activityLogger;
        initializeNextId();
    }

    public SyncService(StoreManager store, BankingProtocol bankingProtocol,
                       TransactionService transactionService, AccountService accountService,
                       ActivityLogger activityLogger, PinStorage ignoredPinStorage) {
        this(store, bankingProtocol, transactionService, accountService, activityLogger);
    }

    private void initializeNextId() {
        nextId = store.getRoot().getSyncLogs().stream()
                .mapToLong(SyncLog::getId)
                .max()
                .orElse(0) + 1;
    }

    public synchronized SyncResult syncAccount(Long accountId, String pin) {
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

    private SyncResult syncAccountInternal(Long accountId, String pin) {
        BankAccount account = store.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        if (pin == null || pin.isBlank()) {
            throw new IllegalArgumentException("A PIN is required for each bank sync.");
        }

        SyncLog syncLog = SyncLog.builder()
                .id(nextId++)
                .bankAccountId(accountId)
                .startedAt(LocalDateTime.now())
                .status(SyncStatus.PARTIAL)
                .build();
        store.getRoot().getSyncLogs().add(syncLog);
        store.store(store.getRoot().getSyncLogs());

        List<Long> newTransactionIds = new ArrayList<>();
        try {
            List<Transaction> fetchedTransactions = bankingProtocol.fetchTransactions(account, pin);
            if (fetchedTransactions == null) {
                throw new BankingException(BankingException.FailureReason.INCOMPLETE_RESPONSE,
                        "Bank returned no complete transaction response.");
            }
            for (Transaction fetched : fetchedTransactions) {
                if (fetched == null || (fetched.getAccountId() != null && !accountId.equals(fetched.getAccountId()))) {
                    throw new BankingException(BankingException.FailureReason.ACCOUNT_MISMATCH,
                            "Bank returned transaction data for an unexpected account.");
                }
                fetched.setAccountId(accountId);
            }

            List<Transaction> localTransactions = new ArrayList<>(transactionService.getTransactionsByAccount(accountId));
            Set<Transaction> matchedTransactions = Collections.newSetFromMap(new IdentityHashMap<>());
            int newCount = 0;
            int skippedCount = 0;

            for (Transaction fetched : fetchedTransactions) {
                Transaction existing = localTransactions.stream()
                        .filter(candidate -> !matchedTransactions.contains(candidate))
                        .filter(candidate -> matches(candidate, fetched))
                        .findFirst()
                        .orElse(null);

                if (existing == null) {
                    Transaction created = transactionService.createTransaction(fetched);
                    localTransactions.add(created);
                    matchedTransactions.add(created);
                    newTransactionIds.add(created.getId());
                    newCount++;
                } else {
                    transactionService.updateFromBank(existing, fetched);
                    matchedTransactions.add(existing);
                    skippedCount++;
                }
            }

            List<Long> stalePendingIds = localTransactions.stream()
                    .filter(transaction -> !transaction.isBooked())
                    .filter(transaction -> !matchedTransactions.contains(transaction))
                    .map(Transaction::getId)
                    .filter(id -> id != null)
                    .toList();
            transactionService.deleteTransactions(stalePendingIds);

            LocalDateTime completedAt = LocalDateTime.now();
            syncLog.setCompletedAt(completedAt);
            syncLog.setStatus(SyncStatus.SUCCESS);
            syncLog.setNewCount(newCount);
            syncLog.setSkippedCount(skippedCount);
            accountService.updateLastSync(accountId, completedAt);
            store.store(syncLog);
            activityLogger.log(ActivityAction.SYNC, EntityType.ACCOUNT, accountId, "Bank sync completed");
        } catch (Exception e) {
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setErrorMessage(safeFailureMessage(e));
            store.store(syncLog);
            activityLogger.log(ActivityAction.SYNC, EntityType.ACCOUNT, accountId,
                    "Bank sync failed: " + safeFailureMessage(e));
            log.error("Bank sync failed for account {} [{}]:\n{}", accountId, failureReason(e),
                    redactedStackTrace(e));
        }

        return new SyncResult(syncLog, newTransactionIds);
    }

    private boolean matches(Transaction stored, Transaction received) {
        String storedChecksum = normalizeChecksum(stored.getChecksum());
        String receivedChecksum = normalizeChecksum(received.getChecksum());
        if (storedChecksum != null && receivedChecksum != null) {
            return storedChecksum.equals(receivedChecksum);
        }

        return java.util.Objects.equals(stored.getBookingDate(), received.getBookingDate())
                && stored.getAmount() != null
                && received.getAmount() != null
                && stored.getAmount().compareTo(received.getAmount()) == 0
                && normalizeText(stored.getOriginalPayee()).equals(normalizeText(received.getOriginalPayee()))
                && normalizeText(stored.getPurpose()).equals(normalizeText(received.getPurpose()));
    }

    private String normalizeChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return null;
        }
        return checksum.trim();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String safeFailureMessage(Exception exception) {
        BankingException.FailureReason reason = failureReason(exception);
        String causeType = rootCauseType(exception);
        if (reason == BankingException.FailureReason.TAN_REQUIRED) {
            return I18n.get("message.sync.tanRequiredDiagnostic", causeType);
        }
        return I18n.get("message.sync.failedDiagnostic", reason.name(), causeType);
    }

    private BankingException.FailureReason failureReason(Throwable error) {
        return error instanceof BankingException bankingException
                ? bankingException.getFailureReason() : BankingException.FailureReason.UNKNOWN;
    }

    private Throwable rootCause(Throwable error) {
        Throwable cause = error;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (cause.getCause() != null && seen.add(cause) && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private String rootCauseType(Throwable error) {
        if (error instanceof BankingException && error.getCause() == null) {
            return "BankResponse";
        }
        return rootCause(error).getClass().getSimpleName();
    }

    private String redactedStackTrace(Throwable error) {
        StringBuilder stackTrace = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cause = error;
        while (cause != null && seen.add(cause)) {
            if (stackTrace.length() > 0) {
                stackTrace.append("\nCaused by: ");
            }
            stackTrace.append(cause.getClass().getName());
            for (StackTraceElement frame : cause.getStackTrace()) {
                stackTrace.append("\n\tat ").append(frame);
            }
            cause = cause.getCause();
        }
        return stackTrace.toString();
    }

    public List<SyncLog> getSyncLogs(Long accountId) {
        return store.getRoot().getSyncLogs().stream()
                .filter(log -> log.getBankAccountId().equals(accountId))
                .toList();
    }
}
