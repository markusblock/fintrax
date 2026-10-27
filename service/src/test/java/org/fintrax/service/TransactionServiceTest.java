package org.fintrax.service;

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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {
    private Path tempDir;
    private StoreManager store;
    private ActivityLogger activityLogger;
    private TransactionService transactionService;
    private AccountService accountService;
    private CategoryService categoryService;
    private LabelService labelService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-test");
        store = new StoreManager(tempDir);
        activityLogger = new ActivityLogger(store);
        transactionService = new TransactionService(store, activityLogger);
        accountService = new AccountService(store, activityLogger);
        categoryService = new CategoryService(store, activityLogger);
        labelService = new LabelService(store, activityLogger);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.shutdown();
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> {
            try { Files.delete(p); } catch (Exception e) {}
        });
    }

    @Test
    void testCreateTransaction() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);

        Transaction tx = Transaction.builder()
                .accountId(account.getId())
                .originalPayee("REWE")
                .purpose("Groceries")
                .amount(BigDecimal.valueOf(-45.67))
                .bookingDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .checksum("abc123")
                .build();

        Transaction created = transactionService.createTransaction(tx);

        assertNotNull(created.getId());
        assertEquals("REWE", created.getOriginalPayee());
    }

    @Test
    void testFilterByDateRange() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);

        transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("A").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.of(2024, 1, 15)).valueDate(LocalDate.of(2024, 1, 15))
                .checksum("c1").build());

        transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("B").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.of(2024, 3, 15)).valueDate(LocalDate.of(2024, 3, 15))
                .checksum("c2").build());

        List<Transaction> filtered = transactionService.filterTransactions(
                null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                null, null, null, null, null);

        assertEquals(1, filtered.size());
        assertEquals("A", filtered.get(0).getOriginalPayee());
    }

    @Test
    void dateRangeExcludesTransactionsWithoutBookingDate() {
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("dated").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.of(2024, 1, 15)).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("undated").amount(BigDecimal.ONE).build());

        List<Transaction> filtered = transactionService.filterTransactions(
                null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                null, null, null, null, null);

        assertEquals(List.of("dated"), filtered.stream().map(Transaction::getOriginalPayee).toList());
    }

    @Test
    void testFilterBySearchText() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);

        transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("REWE Markt").purpose("Einkauf")
                .amount(BigDecimal.ONE).bookingDate(LocalDate.now()).valueDate(LocalDate.now())
                .checksum("c1").build());

        transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("Shell Tank").purpose("Fuel")
                .amount(BigDecimal.ONE).bookingDate(LocalDate.now()).valueDate(LocalDate.now())
                .checksum("c2").build());

        List<Transaction> filtered = transactionService.filterTransactions(
                null, null, null, null, null, null, null, "rewe");

        assertEquals(1, filtered.size());
        assertEquals("REWE Markt", filtered.get(0).getOriginalPayee());
    }

    @Test
    void testUpdateCategory() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);
        Category category = categoryService.createCategory("Food", null, null);

        Transaction tx = transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("REWE").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.now()).valueDate(LocalDate.now()).checksum("c1").build());

        Transaction updated = transactionService.updateCategory(tx.getId(), category.getId());

        assertEquals(category.getId(), updated.getCategoryId());
    }

    @Test
    void userCanStillSetTransactionNoteManually() {
        Transaction transaction = transactionService.createTransaction(Transaction.builder()
                .originalPayee("Manual note").amount(BigDecimal.ONE).build());

        Transaction updated = transactionService.updateNote(transaction.getId(), "Remember receipt");

        assertEquals("Remember receipt", updated.getNote());
    }

    @Test
    void testBulkUpdateCategory() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);
        Category category = categoryService.createCategory("Food", null, null);

        Transaction tx1 = transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("A").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.now()).valueDate(LocalDate.now()).checksum("c1").build());
        Transaction tx2 = transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("B").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.now()).valueDate(LocalDate.now()).checksum("c2").build());

        transactionService.bulkUpdateCategory(List.of(tx1.getId(), tx2.getId()), category.getId());

        assertEquals(category.getId(), transactionService.getTransaction(tx1.getId()).get().getCategoryId());
        assertEquals(category.getId(), transactionService.getTransaction(tx2.getId()).get().getCategoryId());
    }

    @Test
    void bulkAddLabelAddsLabelToEachTransaction() {
        Transaction tx1 = transactionService.createTransaction(Transaction.builder()
                .originalPayee("A").amount(BigDecimal.ONE).labelIds(Set.of(1L)).build());
        Transaction tx2 = transactionService.createTransaction(Transaction.builder()
                .originalPayee("B").amount(BigDecimal.ONE).labelIds(Set.of()).build());

        transactionService.bulkAddLabel(List.of(tx1.getId(), tx2.getId()), 2L);

        assertEquals(Set.of(1L, 2L), transactionService.getTransaction(tx1.getId()).orElseThrow().getLabelIds());
        assertEquals(Set.of(2L), transactionService.getTransaction(tx2.getId()).orElseThrow().getLabelIds());
    }

    @Test
    void bulkRemoveLabelRemovesOnlyRequestedLabel() {
        Transaction tx = transactionService.createTransaction(Transaction.builder()
                .originalPayee("A").amount(BigDecimal.ONE).labelIds(Set.of(1L, 2L)).build());

        transactionService.bulkRemoveLabel(List.of(tx.getId()), 1L);

        assertEquals(Set.of(2L), transactionService.getTransaction(tx.getId()).orElseThrow().getLabelIds());
    }
}
