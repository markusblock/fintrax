package org.fintrax.store;

import org.fintrax.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StoreManagerTest {
    private Path tempDir;
    private StoreManager storeManager;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-test");
        storeManager = new StoreManager(tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storeManager != null) {
            storeManager.shutdown();
        }
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                            // ignore
                        }
                    });
        }
    }

    @Test
    void testInitialization() {
        assertNotNull(storeManager.getRoot());
        assertNotNull(storeManager.getRoot().getAccounts());
        assertNotNull(storeManager.getRoot().getTransactions());
        assertNotNull(storeManager.getRoot().getCategories());
    }

    @Test
    void testStoreAndRetrieveAccount() {
        BankAccount account = BankAccount.builder()
                .id(1L)
                .iban("DE89370400440532013000")
                .bic("COBADEFFXXX")
                .bankName("Commerzbank")
                .accountHolder("Max Mustermann")
                .accountType(AccountType.GIRO)
                .balance(BigDecimal.valueOf(1500.50))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        storeManager.getRoot().getAccounts().add(account);
        storeManager.store(storeManager.getRoot().getAccounts());

        BankAccount retrieved = storeManager.getRoot().getAccounts().stream()
                .filter(a -> a.getId() == 1L)
                .findFirst()
                .orElse(null);
        assertNotNull(retrieved);
        assertEquals("DE89370400440532013000", retrieved.getIban());
        assertEquals("Commerzbank", retrieved.getBankName());
    }

    @Test
    void testStoreAndRetrieveTransaction() {
        BankAccount account = BankAccount.builder()
                .id(1L)
                .iban("DE89370400440532013000")
                .bankName("Test Bank")
                .accountHolder("Test User")
                .accountType(AccountType.GIRO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Transaction transaction = Transaction.builder()
                .id(1L)
                .accountId(1L)
                .originalPayee("REWE MARKT")
                .amount(BigDecimal.valueOf(-45.67))
                .bookingDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .checksum("abc123")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        storeManager.getRoot().getAccounts().add(account);
        storeManager.getRoot().getTransactions().add(transaction);
        storeManager.store(storeManager.getRoot().getAccounts());
        storeManager.store(storeManager.getRoot().getTransactions());

        Transaction retrieved = storeManager.getRoot().getTransactions().stream()
                .filter(t -> t.getId() == 1L)
                .findFirst()
                .orElse(null);
        assertNotNull(retrieved);
        assertEquals("REWE MARKT", retrieved.getOriginalPayee());
        assertEquals(BigDecimal.valueOf(-45.67), retrieved.getAmount());
    }

    @Test
    void testActivityLogFifo() {
        for (int i = 0; i < 10_500; i++) {
            ActivityLog log = ActivityLog.builder()
                    .id((long) i)
                    .timestamp(LocalDateTime.now())
                    .action(ActivityAction.CREATE)
                    .entityType(EntityType.ACCOUNT)
                    .description("Test log " + i)
                    .build();
            storeManager.addActivityLog(log);
        }

        assertTrue(storeManager.getRoot().getActivityLogs().size() <= 10_000,
                "Activity logs should be capped at 10,000");
    }

    @Test
    void testPersistenceAcrossRestart() {
        BankAccount account = BankAccount.builder()
                .id(99L)
                .iban("DE12345678901234567890")
                .bankName("Persistence Test Bank")
                .accountHolder("Test")
                .accountType(AccountType.SAVINGS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        storeManager.getRoot().getAccounts().add(account);
        storeManager.store(storeManager.getRoot().getAccounts());
        storeManager.shutdown();

        StoreManager reloaded = new StoreManager(tempDir);
        BankAccount retrieved = reloaded.getRoot().getAccounts().stream()
                .filter(a -> a.getId() == 99L)
                .findFirst()
                .orElse(null);
        assertNotNull(retrieved);
        assertEquals("Persistence Test Bank", retrieved.getBankName());

        reloaded.shutdown();
        storeManager = null;
    }

    @Test
    void resetCategoriesRulesAndLabelsClearsRetainedTransactionReferences() {
        Category category = Category.builder().id(1L).name("Food").build();
        Label label = Label.builder().id(2L).name("Important").build();
        Rule rule = Rule.builder().id(3L).name("Food rule").build();
        Transaction transaction = Transaction.builder()
                .id(4L)
                .categoryId(category.getId())
                .labelIds(new HashSet<>(Set.of(label.getId())))
                .build();

        storeManager.getRoot().getCategories().add(category);
        storeManager.getRoot().getLabels().add(label);
        storeManager.getRoot().getRules().add(rule);
        storeManager.getRoot().getTransactions().add(transaction);
        storeManager.store(storeManager.getRoot());

        storeManager.reset(Set.of(ResetGroup.CATEGORIES_RULES_LABELS));

        assertAll(
                () -> assertTrue(storeManager.getRoot().getCategories().isEmpty()),
                () -> assertTrue(storeManager.getRoot().getLabels().isEmpty()),
                () -> assertTrue(storeManager.getRoot().getRules().isEmpty()),
                () -> assertNull(transaction.getCategoryId()),
                () -> assertTrue(transaction.getLabelIds().isEmpty()),
                () -> assertNull(storeManager.getCategory(category.getId()))
        );
        assertNotNull(storeManager.getTransaction(transaction.getId()));
    }

    @Test
    void resetAccountsTransactionsAndHistoryClearsOnlyThatGroup() {
        storeManager.getRoot().getAccounts().add(BankAccount.builder().id(1L).build());
        storeManager.getRoot().getTransactions().add(Transaction.builder().id(2L).build());
        storeManager.getRoot().getCategories().add(Category.builder().id(3L).name("Food").build());
        storeManager.getRoot().getSyncLogs().add(SyncLog.builder().id(4L).build());
        storeManager.getRoot().getActivityLogs().add(ActivityLog.builder().id(5L).build());
        storeManager.store(storeManager.getRoot());

        storeManager.reset(Set.of(ResetGroup.ACCOUNTS_TRANSACTIONS_HISTORY));

        assertAll(
                () -> assertTrue(storeManager.getRoot().getAccounts().isEmpty()),
                () -> assertTrue(storeManager.getRoot().getTransactions().isEmpty()),
                () -> assertTrue(storeManager.getRoot().getSyncLogs().isEmpty()),
                () -> assertTrue(storeManager.getRoot().getActivityLogs().isEmpty()),
                () -> assertEquals(1, storeManager.getRoot().getCategories().size())
        );
    }

    @Test
    void resetSettingsClearsOnlyPersistedSettings() {
        storeManager.getRoot().getSettings().put("columns", AppSetting.builder().key("columns").value("saved").build());
        storeManager.getRoot().getCategories().add(Category.builder().id(1L).name("Food").build());
        storeManager.store(storeManager.getRoot());

        storeManager.reset(Set.of(ResetGroup.APPLICATION_SETTINGS));

        assertTrue(storeManager.getRoot().getSettings().isEmpty());
        assertEquals(1, storeManager.getRoot().getCategories().size());
    }

    @Test
    void resetRequiresAtLeastOneGroup() {
        assertThrows(IllegalArgumentException.class, () -> storeManager.reset(Set.of()));
    }
}
