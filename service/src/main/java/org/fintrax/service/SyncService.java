package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.BankingSyncData;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

@Slf4j
@Service
public class SyncService {
    private static final String DIAGNOSTIC_DETAILS_MARKER = "\n[FINTRAX_SYNC_DIAGNOSTICS]\n";
    private final StoreManager store;
    private final BankingProtocol bankingProtocol;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final ActivityLogger activityLogger;
    private final BankService bankService;
    private final AtomicInteger activeSyncs = new AtomicInteger();
    private long nextId = 1;

    @Autowired
    public SyncService(StoreManager store, BankingProtocol bankingProtocol,
                       TransactionService transactionService, AccountService accountService,
                       ActivityLogger activityLogger, BankService bankService) {
        this.store = store;
        this.bankingProtocol = bankingProtocol;
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.activityLogger = activityLogger;
        this.bankService = bankService;
        initializeNextId();
    }

    public SyncService(StoreManager store, BankingProtocol bankingProtocol,
                       TransactionService transactionService, AccountService accountService,
                       ActivityLogger activityLogger) {
        this(store, bankingProtocol, transactionService, accountService, activityLogger, (BankService) null);
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
        return syncAccount(accountId, pin, null);
    }

    public synchronized SyncResult syncAccount(Long accountId, String pin, Consumer<SyncProgress> progress) {
        return syncAccount(accountId, pin, progress, () -> false);
    }

    public synchronized SyncResult syncAccount(Long accountId, String pin, Consumer<SyncProgress> progress,
                                               BooleanSupplier cancellationRequested) {
        activeSyncs.incrementAndGet();
        try {
            return syncAccountInternal(accountId, pin, progress, cancellationRequested);
        } finally {
            activeSyncs.decrementAndGet();
        }
    }

    public boolean isSyncing() {
        return activeSyncs.get() > 0;
    }

    private SyncResult syncAccountInternal(Long accountId, String pin, Consumer<SyncProgress> progress,
                                           BooleanSupplier cancellationRequested) {
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
                .stage(SyncStage.CONNECTION)
                .build();
        store.getRoot().getSyncLogs().add(syncLog);
        store.store(store.getRoot().getSyncLogs());
        reportProgress(progress, accountId, SyncStage.CONNECTION, 0, 0, 0);

        List<Long> newTransactionIds = new ArrayList<>();
        AtomicReference<SyncStage> currentStage = new AtomicReference<>(SyncStage.CONNECTION);
        int downloadedCount = 0;
        int newCount = 0;
        int skippedCount = 0;
        try {
            throwIfCancelled(cancellationRequested);
            Bank bank = bankService == null ? null : bankService.getBankForAccount(account).orElse(null);
            if (bankService != null && bank == null) {
                throw new BankingException(BankingException.FailureReason.MISSING_BANK_PROFILE,
                        SyncStage.CONNECTION, "No bank profile is linked to this account.");
            }
            if (bank != null && (bank.getFintsUserId() == null || bank.getFintsUserId().isBlank())) {
                throw new BankingException(BankingException.FailureReason.MISSING_USER_ID,
                        SyncStage.AUTHENTICATION, "FinTS user ID is missing from the bank profile.");
            }
            currentStage.set(SyncStage.AUTHENTICATION);
            reportProgress(progress, accountId, currentStage.get(), 0, 0, 0);
            Consumer<SyncStage> bankingProgress = bankStage -> {
                currentStage.set(bankStage);
                reportProgress(progress, accountId, bankStage, 0, 0, 0);
            };
            throwIfCancelled(cancellationRequested);
            BankingSyncData syncData = bankService == null
                    ? bankingProtocol.fetchSyncData(account, pin, bankingProgress)
                    : bankingProtocol.fetchSyncData(account, bank, pin, bankingProgress);
            currentStage.set(SyncStage.DATA_RETRIEVAL);
            syncLog.setStage(currentStage.get());
            if (syncData == null || syncData.transactions() == null) {
                throw new BankingException(BankingException.FailureReason.INCOMPLETE_RESPONSE, currentStage.get(),
                        "Bank returned no complete transaction response.");
            }
            throwIfCancelled(cancellationRequested);
            List<Transaction> fetchedTransactions = syncData.transactions();
            downloadedCount = fetchedTransactions.size();
            reportProgress(progress, accountId, SyncStage.DATA_RETRIEVAL, downloadedCount, 0, 0);
            for (Transaction fetched : fetchedTransactions) {
                throwIfCancelled(cancellationRequested);
                if (fetched == null || (fetched.getAccountId() != null && !accountId.equals(fetched.getAccountId()))) {
                    throw new BankingException(BankingException.FailureReason.ACCOUNT_MISMATCH,
                            currentStage.get(),
                            "Bank returned transaction data for an unexpected account.");
                }
                fetched.setAccountId(accountId);
            }

            currentStage.set(SyncStage.DATA_IMPORT);
            syncLog.setStage(currentStage.get());
            reportProgress(progress, accountId, currentStage.get(), downloadedCount, 0, 0);
            List<Transaction> localTransactions = new ArrayList<>(transactionService.getTransactionsByAccount(accountId));
            Set<Transaction> matchedTransactions = Collections.newSetFromMap(new IdentityHashMap<>());

            for (Transaction fetched : fetchedTransactions) {
                throwIfCancelled(cancellationRequested);
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
                reportProgress(progress, accountId, SyncStage.DATA_IMPORT, downloadedCount,
                        newCount, skippedCount);
            }

            List<Long> stalePendingIds = localTransactions.stream()
                    .filter(transaction -> !transaction.isBooked())
                    .filter(transaction -> !matchedTransactions.contains(transaction))
                    .map(Transaction::getId)
                    .filter(id -> id != null)
                    .toList();
            throwIfCancelled(cancellationRequested);
            transactionService.deleteTransactions(stalePendingIds);
            if (syncData.accountBalance() != null) {
                accountService.updateBalance(accountId, syncData.accountBalance(), syncData.balanceDate());
            }

            LocalDateTime completedAt = LocalDateTime.now();
            syncLog.setCompletedAt(completedAt);
            syncLog.setStatus(SyncStatus.SUCCESS);
            syncLog.setStage(SyncStage.COMPLETE);
            syncLog.setNewCount(newCount);
            syncLog.setSkippedCount(skippedCount);
            accountService.updateLastSync(accountId, completedAt);
            store.store(syncLog);
            reportProgress(progress, accountId, SyncStage.COMPLETE, downloadedCount, newCount, skippedCount);
            activityLogger.log(ActivityAction.SYNC, EntityType.ACCOUNT, accountId, "Bank sync completed");
        } catch (Exception e) {
            if (isCancellationRequested(cancellationRequested)) {
                syncLog.setCompletedAt(LocalDateTime.now());
                syncLog.setStatus(SyncStatus.PARTIAL);
                syncLog.setStage(currentStage.get());
                syncLog.setNewCount(newCount);
                syncLog.setSkippedCount(skippedCount);
                syncLog.setErrorMessage(I18n.get("message.sync.cancelled"));
                store.store(syncLog);
                activityLogger.log(ActivityAction.SYNC, EntityType.ACCOUNT, accountId, "Bank sync cancelled");
                log.info("Bank sync cancelled for account {}", accountId);
            } else {
                SyncStage stage = failureStage(e, currentStage.get());
                syncLog.setCompletedAt(LocalDateTime.now());
                syncLog.setStatus(SyncStatus.FAILED);
                syncLog.setStage(stage);
                syncLog.setErrorMessage(safeFailureMessage(e));
                store.store(syncLog);
                activityLogger.log(ActivityAction.SYNC, EntityType.ACCOUNT, accountId,
                        detailedFailureMessage(stage, e));
                log.error("Bank sync failed for account {} [{}]:\n{}", accountId, failureReason(e),
                        redactedStackTrace(e));
            }
        }

        return new SyncResult(syncLog, newTransactionIds);
    }

    private void reportProgress(Consumer<SyncProgress> progress, Long accountId, SyncStage stage,
                                int downloadedCount, int importedCount, int alreadyKnownCount) {
        if (progress != null) {
            progress.accept(new SyncProgress(accountId, stage, downloadedCount, importedCount, alreadyKnownCount));
        }
    }

    private void throwIfCancelled(BooleanSupplier cancellationRequested) {
        if (isCancellationRequested(cancellationRequested)) {
            throw new CancellationException("Bank sync cancelled");
        }
    }

    private boolean isCancellationRequested(BooleanSupplier cancellationRequested) {
        return (cancellationRequested != null && cancellationRequested.getAsBoolean())
                || Thread.currentThread().isInterrupted();
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
        if (exception instanceof BankingException bankingException) {
            if (bankingException.getFailureReason() == BankingException.FailureReason.MISSING_BANK_PROFILE) {
                return I18n.get("message.sync.bankProfileMissing");
            }
            if (bankingException.getFailureReason() == BankingException.FailureReason.MISSING_USER_ID) {
                return I18n.get("message.sync.userIdMissing");
            }
        }
        BankingException.FailureReason reason = failureReason(exception);
        String causeType = rootCauseType(exception);
        if (reason == BankingException.FailureReason.TAN_REQUIRED) {
            return I18n.get("message.sync.tanRequiredDiagnostic", causeType);
        }
        return I18n.get("message.sync.failedDiagnostic", reason.name(), causeType);
    }

    private SyncStage failureStage(Exception exception, SyncStage currentStage) {
        if (exception instanceof BankingException bankingException) {
            if (bankingException.getSyncStage() != null) {
                return bankingException.getSyncStage();
            }
            return switch (bankingException.getFailureReason()) {
                case TAN_REQUIRED -> SyncStage.AUTHENTICATION;
                case BANK_REJECTED, INCOMPLETE_RESPONSE, MAPPING_FAILED, ACCOUNT_MISMATCH -> SyncStage.DATA_RETRIEVAL;
                default -> currentStage;
            };
        }
        return currentStage;
    }

    private String detailedFailureMessage(SyncStage stage, Exception exception) {
        String stageLabel = I18n.get("sync.failureStage." + stage.name());
        String reason = failureReason(exception).name();
        String causeType = rootCauseType(exception);
        String diagnostics = DIAGNOSTIC_DETAILS_MARKER + I18n.get("activity.sync.stackTraceHeader")
                + "\n" + redactedStackTrace(exception);
        return I18n.get("activity.sync.failureDetails", stageLabel, reason, causeType,
                diagnostics);
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
