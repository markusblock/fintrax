package org.fintrax.service;

import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.PinStorage;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

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
                activityLogger, new PinStorage(tempDir));
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
    void failedSyncReturnsFailureDetailsAndNoReviewableTransactions() {
        BankAccount account = createAccount();
        bankingProtocol.failure = new BankingException("bank unavailable");

        SyncResult result = syncService.syncAccount(account.getId(), "1234");

        assertEquals(SyncStatus.FAILED, result.syncLog().getStatus());
        assertEquals("bank unavailable", result.syncLog().getErrorMessage());
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
        private BankingException failure;

        @Override
        public List<Transaction> fetchTransactions(BankAccount account, String pin) throws BankingException {
            if (failure != null) throw failure;
            return transactions;
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
