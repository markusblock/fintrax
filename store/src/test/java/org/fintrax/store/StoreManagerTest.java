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
}
