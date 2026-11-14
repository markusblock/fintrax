package org.fintrax.service;

import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.BankingSyncData;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SyncServiceTest {
    private Path tempDir;
    private StoreManager store;
    private AccountService accountService;
    private TransactionService transactionService;
    private RuleEngine ruleEngine;
    private FakeBankingProtocol bankingProtocol;
    private SyncService syncService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-sync-test");
        store = new StoreManager(tempDir);
        ActivityLogger activityLogger = new ActivityLogger(store);
        accountService = new AccountService(store, activityLogger);
        transactionService = new TransactionService(store, activityLogger);
        ruleEngine = new RuleEngine(store, activityLogger);
        bankingProtocol = new FakeBankingProtocol();
        syncService = new SyncService(store, bankingProtocol, transactionService, accountService,
                activityLogger);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.shutdown();
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
            try { Files.delete(path); } catch (Exception ignored) {}
        });
    }

    @Test
    void syncReturnsNewTransactionIdsWithoutApplyingRules() {
        BankAccount account = createAccount();
        Category category = Category.builder().id(1L).name("Food").build();
        store.getRoot().getCategories().add(category);
        store.getRoot().getRules().add(Rule.builder().id(1L).name("Shop")
                .enabled(true).priority(1)
                .conditions(List.of(RuleCondition.builder().matchField(MatchField.PAYEE_NAME)
                        .operator(Operator.CONTAINS).value("SHOP").build()))
                .actions(List.of(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(category.getId()).build())).build());
        transactionService.createTransaction(transaction(account.getId(), "old-checksum", "SHOP old"));
        bankingProtocol.transactions = List.of(transaction(account.getId(), "new-checksum", "SHOP"));

        SyncResult result = syncService.syncAccount(account.getId(), "1234");

        assertEquals(SyncStatus.SUCCESS, result.syncLog().getStatus());
        assertEquals(1, result.syncLog().getNewCount());
        assertEquals(1, result.newTransactionIds().size());
        Transaction imported = transactionService.getTransaction(result.newTransactionIds().get(0)).orElseThrow();
        assertNull(imported.getCategoryId());
        RuleApplicationPreview scopedPreview = ruleEngine.previewRulesForTransactions(
                result.newTransactionIds(), true);
        assertEquals(List.of(imported.getId()), scopedPreview.matchingTransactions()
                .stream().map(TransactionRulePreview::transactionId).toList());
    }

    @Test
    void syncDoesNotIncludeSkippedTransactionsInReviewScope() {
        BankAccount account = createAccount();
        transactionService.createTransaction(transaction(account.getId(), "same-checksum", "Existing"));
        bankingProtocol.transactions = List.of(transaction(account.getId(), "same-checksum", "Existing"));

        SyncResult result = syncService.syncAccount(account.getId(), "1234");

        assertEquals(0, result.syncLog().getNewCount());
        assertEquals(1, result.syncLog().getSkippedCount());
        assertTrue(result.newTransactionIds().isEmpty());
    }

    @Test
    void syncUpgradesMatchingPendingTransactionWithoutDuplicatingIt() {
        BankAccount account = createAccount();
        Transaction pending = transaction(account.getId(), "same-checksum", "Pending payee");
        pending.setBooked(false);
        transactionService.createTransaction(pending);
        Transaction booked = transaction(account.getId(), "same-checksum", "Booked payee");
        bankingProtocol.transactions = List.of(booked);

        SyncResult result = syncService.syncAccount(account.getId(), "1234");

        List<Transaction> stored = transactionService.getTransactionsByAccount(account.getId());
        assertEquals(1, stored.size());
        assertEquals(pending.getId(), stored.getFirst().getId());
        assertTrue(stored.getFirst().isBooked());
        assertEquals("Booked payee", stored.getFirst().getOriginalPayee());
        assertEquals(0, result.syncLog().getNewCount());
        assertEquals(1, result.syncLog().getSkippedCount());
    }

    @Test
    void syncDeletesStalePendingTransactionsOnlyAfterCompleteResponse() {
        BankAccount account = createAccount();
        Transaction pending = transaction(account.getId(), "stale-pending", "Pending");
        pending.setBooked(false);
        transactionService.createTransaction(pending);
        Transaction booked = transactionService.createTransaction(
                transaction(account.getId(), "old-booked", "Booked"));
        bankingProtocol.transactions = List.of(transaction(account.getId(), "incoming", "Incoming"));

        SyncResult result = syncService.syncAccount(account.getId(), "1234");

        List<Transaction> stored = transactionService.getTransactionsByAccount(account.getId());
        assertEquals(SyncStatus.SUCCESS, result.syncLog().getStatus());
        assertTrue(stored.stream().noneMatch(transaction -> pending.getId().equals(transaction.getId())));
        assertTrue(stored.stream().anyMatch(transaction -> booked.getId().equals(transaction.getId())));
        assertTrue(stored.stream().anyMatch(transaction -> "incoming".equals(transaction.getChecksum())));
    }

    @Test
    void failedSyncRetainsPendingTransactions() {
        BankAccount account = createAccount();
        Transaction pending = transaction(account.getId(), "pending", "Pending");
        pending.setBooked(false);
        transactionService.createTransaction(pending);
        bankingProtocol.failure = new BankingException("bank unavailable");

        SyncResult result = syncService.syncAccount(account.getId(), "1234");

        assertEquals(SyncStatus.FAILED, result.syncLog().getStatus());
        assertEquals(1, transactionService.getTransactionsByAccount(account.getId()).size());
        assertFalse(transactionService.getTransactionsByAccount(account.getId()).getFirst().isBooked());
    }

    @Test
    void syncUpdatesAccountBalanceFromBankReportedSnapshot() {
        BankAccount account = createAccount();
        Transaction transactionWithDifferentBalance = transaction(account.getId(), "txn", "Purchase");
        transactionWithDifferentBalance.setBalanceAfter(new BigDecimal("56.78"));
        bankingProtocol.transactions = List.of(transactionWithDifferentBalance);
        bankingProtocol.accountBalance = new BigDecimal("123.45");
        bankingProtocol.balanceDate = LocalDateTime.of(2026, 1, 2, 10, 30);

        syncService.syncAccount(account.getId(), "1234");

        assertEquals(new BigDecimal("123.45"), account.getBalance());
        assertEquals(LocalDateTime.of(2026, 1, 2, 10, 30), account.getBalanceDate());
    }

    @Test
    void syncKeepsAccountBalanceWhenBankReturnsNoAccountBalance() {
        BankAccount account = createAccount();
        LocalDateTime balanceDate = LocalDateTime.of(2026, 1, 1, 10, 30);
        accountService.updateBalance(account.getId(), new BigDecimal("42.00"), balanceDate);
        Transaction transactionWithBalance = transaction(account.getId(), "txn", "Purchase");
        transactionWithBalance.setBalanceAfter(new BigDecimal("99.00"));
        bankingProtocol.transactions = List.of(transactionWithBalance);

        syncService.syncAccount(account.getId(), "1234");

        assertEquals(new BigDecimal("42.00"), account.getBalance());
        assertEquals(balanceDate, account.getBalanceDate());
    }

    @Test
    void syncExcludedAccountIsNotContactedOrChanged() {
        BankAccount account = createAccount();
        Transaction existing = transactionService.createTransaction(transaction(account.getId(), "existing", "Local"));
        accountService.updateBalance(account.getId(), new BigDecimal("42.00"),
                LocalDateTime.of(2026, 1, 1, 10, 30));
        accountService.updateLastSync(account.getId(), LocalDateTime.of(2026, 1, 2, 10, 30));
        accountService.setSyncExcluded(account.getId(), true);
        bankingProtocol.accountBalance = new BigDecimal("999.00");
        bankingProtocol.transactions = List.of(transaction(account.getId(), "bank", "Bank"));
        Long accountId = account.getId();

        assertThrows(IllegalStateException.class, () -> syncService.syncAccount(accountId, "1234"));

        assertEquals(0, bankingProtocol.fetchCount);
        assertTrue(store.getRoot().getSyncLogs().isEmpty());
        assertEquals(List.of(existing.getId()), transactionService.getTransactionsByAccount(account.getId()).stream()
                .map(Transaction::getId).toList());
        assertEquals(new BigDecimal("42.00"), account.getBalance());
        assertEquals(LocalDateTime.of(2026, 1, 2, 10, 30), account.getLastSyncAt());
    }

    @Test
    void syncReportsProgressStagesInOrder() {
        BankAccount account = createAccount();
        List<SyncProgress> progress = new ArrayList<>();

        syncService.syncAccount(account.getId(), "1234", progress::add);

        assertEquals(List.of(SyncStage.CONNECTION, SyncStage.AUTHENTICATION, SyncStage.DATA_RETRIEVAL,
                SyncStage.DATA_RETRIEVAL, SyncStage.DATA_IMPORT, SyncStage.COMPLETE),
                progress.stream().map(SyncProgress::stage).toList());
    }

    @Test
    void syncProgressReportsDownloadedAndImportedCounts() {
        BankAccount account = createAccount();
        transactionService.createTransaction(transaction(account.getId(), "existing", "Existing"));
        bankingProtocol.transactions = List.of(
                transaction(account.getId(), "existing", "Existing"),
                transaction(account.getId(), "new", "New"));
        List<SyncProgress> progress = new ArrayList<>();

        syncService.syncAccount(account.getId(), "1234", progress::add);

        SyncProgress downloaded = progress.stream()
                .filter(event -> event.stage() == SyncStage.DATA_RETRIEVAL && event.transactionsDownloaded() > 0)
                .findFirst().orElseThrow();
        SyncProgress imported = progress.stream()
                .filter(event -> event.stage() == SyncStage.DATA_IMPORT && event.transactionsImported() > 0)
                .reduce((first, last) -> last).orElseThrow();
        assertEquals(2, downloaded.transactionsDownloaded());
        assertEquals(2, imported.transactionsDownloaded());
        assertEquals(1, imported.transactionsImported());
        assertEquals(1, imported.transactionsAlreadyKnown());
    }

    @Test
    void cancellationDuringImportStopsBeforeNextTransactionAndRecordsPartialSync() {
        BankAccount account = createAccount();
        bankingProtocol.transactions = List.of(
                transaction(account.getId(), "first", "First"),
                transaction(account.getId(), "second", "Second"));
        AtomicBoolean cancellationRequested = new AtomicBoolean();

        SyncResult result = syncService.syncAccount(account.getId(), "1234", progress -> {
            if (progress.stage() == SyncStage.DATA_IMPORT && progress.transactionsImported() == 1) {
                cancellationRequested.set(true);
            }
        }, cancellationRequested::get);

        assertEquals(SyncStatus.PARTIAL, result.syncLog().getStatus());
        assertEquals(SyncStage.DATA_IMPORT, result.syncLog().getStage());
        assertEquals(1, result.syncLog().getNewCount());
        assertEquals(1, transactionService.getTransactionsByAccount(account.getId()).size());
        assertEquals(1, result.newTransactionIds().size());
    }

    @Test
    void failedSyncReturnsSafeFailureDetailsAndNoReviewableTransactions() {
        BankAccount account = createAccount();
        bankingProtocol.failure = new BankingException("bank unavailable");

        SyncResult result = syncService.syncAccount(account.getId(), "1234");

        assertEquals(SyncStatus.FAILED, result.syncLog().getStatus());
        assertTrue(result.syncLog().getErrorMessage().contains("UNKNOWN"));
        assertTrue(result.syncLog().getErrorMessage().contains("BankResponse"));
        assertFalse(result.syncLog().getErrorMessage().contains("bank unavailable"));
        assertTrue(result.newTransactionIds().isEmpty());
    }

    private BankAccount createAccount() {
        return accountService.createAccount("DE89370400440532013000", null, "Bank", "User",
                AccountType.GIRO, null);
    }

    private Transaction transaction(Long accountId, String checksum, String payee) {
        return Transaction.builder().accountId(accountId).checksum(checksum).originalPayee(payee)
                .amount(BigDecimal.TEN).bookingDate(LocalDate.now()).valueDate(LocalDate.now()).build();
    }

    private static final class FakeBankingProtocol implements BankingProtocol {
        private List<Transaction> transactions = List.of();
        private BigDecimal accountBalance;
        private LocalDateTime balanceDate;
        private BankingException failure;
        private int fetchCount;

        @Override
        public List<Transaction> fetchTransactions(BankAccount account, String pin) throws BankingException {
            if (failure != null) throw failure;
            return transactions;
        }

        @Override
        public BankingSyncData fetchSyncData(BankAccount account, String pin) throws BankingException {
            fetchCount++;
            return new BankingSyncData(fetchTransactions(account, pin), accountBalance, balanceDate);
        }

        @Override
        public boolean validatePin(BankAccount account, String pin) {
            return true;
        }

        @Override
        public String getProtocolName() {
            return "test";
        }
    }
}
