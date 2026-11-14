package org.fintrax.service;

import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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
    void updateFromBankStoresAllFinTsTransactionReferences() {
        Transaction stored = transactionService.createTransaction(Transaction.builder().build());
        Transaction received = Transaction.builder()
                .endToEndId("E2E-123")
                .mandateId("MANDATE-123")
                .creditorId("CREDITOR-123")
                .customerReference("CUSTOMER-123")
                .primanota("PRIMANOTA-123")
                .gvCode("166")
                .build();

        transactionService.updateFromBank(stored, received);

        Transaction reloaded = transactionService.getTransaction(stored.getId()).orElseThrow();
        assertEquals("E2E-123", reloaded.getEndToEndId());
        assertEquals("MANDATE-123", reloaded.getMandateId());
        assertEquals("CREDITOR-123", reloaded.getCreditorId());
        assertEquals("CUSTOMER-123", reloaded.getCustomerReference());
        assertEquals("PRIMANOTA-123", reloaded.getPrimanota());
        assertEquals("166", reloaded.getGvCode());
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

        List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                null, null, null, null, null));

        assertEquals(1, filtered.size());
        assertEquals("A", filtered.get(0).getOriginalPayee());
    }

    @Test
    void dateRangeMatchesOnlyBookingDateInclusively() {
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 2, 1);
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("booking date match").amount(BigDecimal.ONE)
                .bookingDate(fromDate).valueDate(toDate.plusDays(1)).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("value date match").amount(BigDecimal.ONE)
                .bookingDate(fromDate.minusDays(1)).valueDate(toDate).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("no date match").amount(BigDecimal.ONE)
                .bookingDate(fromDate.minusDays(1)).valueDate(toDate.plusDays(1)).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("no dates").amount(BigDecimal.ONE).build());

        List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                null, fromDate, toDate, null, null, null, null, null));

        assertEquals(List.of("booking date match"),
                filtered.stream().map(Transaction::getOriginalPayee).toList());
    }

    @Test
    void categoryFilterIncludesSelectedCategoryAndItsDirectChildrenOnly() {
        Category parent = categoryService.createCategory("Household", null, null);
        Category child = categoryService.createCategory("Utilities", parent.getId(), null);
        Category grandchild = categoryService.createCategory("Electricity", child.getId(), null);
        Category unrelated = categoryService.createCategory("Food", null, null);

        transactionService.createTransaction(Transaction.builder()
                .originalPayee("parent").categoryId(parent.getId()).amount(BigDecimal.ONE).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("child").categoryId(child.getId()).amount(BigDecimal.ONE).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("grandchild").categoryId(grandchild.getId()).amount(BigDecimal.ONE).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("unrelated").categoryId(unrelated.getId()).amount(BigDecimal.ONE).build());

        List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                null, null, null, parent.getId(), null, null, null, null));

        assertEquals(List.of("parent", "child"),
                filtered.stream().map(Transaction::getOriginalPayee).toList());
    }

    @Test
    void noCategoryFilterIncludesOnlyTransactionsWithoutCategory() {
        Category category = categoryService.createCategory("Food", null, null);
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("categorized").categoryId(category.getId()).amount(BigDecimal.ONE).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("uncategorized").amount(BigDecimal.ONE).build());

        List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                null, null, null, null, true, null, false, null, null, null));

        assertEquals(List.of("uncategorized"),
                filtered.stream().map(Transaction::getOriginalPayee).toList());
    }

    @Test
    void noLabelsFilterIncludesOnlyTransactionsWithoutLabels() {
        org.fintrax.model.Label label = labelService.createLabel("Urgent", "#1976d2", null);
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("labeled").labelIds(Set.of(label.getId())).amount(BigDecimal.ONE).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("unlabeled").amount(BigDecimal.ONE).build());

        List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                null, null, null, null, false, null, true, null, null, null));

        assertEquals(List.of("unlabeled"),
                filtered.stream().map(Transaction::getOriginalPayee).toList());
    }

    @Test
    void amountFiltersExcludeTransactionsWithoutAmount() {
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("with amount").amount(BigDecimal.TEN).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("without amount").build());

        List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                null, null, null, null, null, BigDecimal.ONE, null, null));

        assertEquals(List.of("with amount"),
                filtered.stream().map(Transaction::getOriginalPayee).toList());
    }

    @Test
    void labelFilterRequiresEverySelectedLabel() {
        org.fintrax.model.Label urgent = labelService.createLabel("Urgent", "#1976d2", null);
        org.fintrax.model.Label travel = labelService.createLabel("Travel", "#cc4400", null);
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("both labels").labelIds(Set.of(urgent.getId(), travel.getId())).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("urgent only").labelIds(Set.of(urgent.getId())).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("travel only").labelIds(Set.of(travel.getId())).build());
        transactionService.createTransaction(Transaction.builder().originalPayee("no labels").build());

        List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                null, null, null, null, Set.of(urgent.getId(), travel.getId()), null, null, null));

        assertEquals(List.of("both labels"), filtered.stream().map(Transaction::getOriginalPayee).toList());
    }

    @Test
    void dateRangeExcludesTransactionsWithoutBookingDate() {
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("dated").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.of(2024, 1, 15)).build());
        transactionService.createTransaction(Transaction.builder()
                .originalPayee("undated").amount(BigDecimal.ONE).build());

        List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1),
                null, null, null, null, null));

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

        List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                null, null, null, null, null, null, null, "rewe"));

        assertEquals(1, filtered.size());
        assertEquals("REWE Markt", filtered.get(0).getOriginalPayee());
    }

    @Test
    void searchMatchesCategoryLabelAmountAndBookingDate() {
        Category category = categoryService.createCategory("Utilities", null, null);
        org.fintrax.model.Label label = labelService.createLabel("Urgent", "#1976d2", null);
        BigDecimal amount = new BigDecimal("1234.56");
        LocalDate bookingDate = LocalDate.of(2024, 2, 10);
        Transaction transaction = transactionService.createTransaction(Transaction.builder()
                .originalPayee("Power company")
                .purpose("Monthly invoice")
                .categoryId(category.getId())
                .labelIds(Set.of(label.getId()))
                .amount(amount)
                .bookingDate(bookingDate)
                .build());

        DateTimeFormatter displayDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());
        for (String searchText : List.of("utilities", "urgent", String.format("%,.2f EUR", amount.doubleValue()),
                amount.toPlainString(),
                bookingDate.toString(), displayDateFormatter.format(bookingDate))) {
            List<Transaction> filtered = transactionService.filterTransactions(new TransactionFilter(
                    null, null, null, null, null, null, null, searchText));
            assertEquals(List.of(transaction), filtered, "search should match: " + searchText);
        }
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
