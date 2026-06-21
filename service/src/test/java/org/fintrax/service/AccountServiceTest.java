package org.fintrax.service;

import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {
    private Path tempDir;
    private StoreManager store;
    private ActivityLogger activityLogger;
    private AccountService accountService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-test");
        store = new StoreManager(tempDir);
        activityLogger = new ActivityLogger(store);
        accountService = new AccountService(store, activityLogger);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.shutdown();
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> {
            try { Files.delete(p); } catch (Exception e) {}
        });
    }

    @Test
    void testCreateAccount() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", "COBADEFFXXX", "Commerzbank",
                "Max Mustermann", AccountType.GIRO, "Test account");

        assertNotNull(account.getId());
        assertEquals("DE89370400440532013000", account.getIban());
        assertEquals("Commerzbank", account.getBankName());
        assertEquals(AccountType.GIRO, account.getAccountType());
    }

    @Test
    void testGetAllAccounts() {
        accountService.createAccount("DE111111111111111111", null, "Bank A", "User A", AccountType.GIRO, null);
        accountService.createAccount("DE222222222222222222", null, "Bank B", "User B", AccountType.SAVINGS, null);

        assertEquals(2, accountService.getAllAccounts().size());
    }

    @Test
    void testUpdateAccount() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", "COBADEFFXXX", "Commerzbank",
                "Max Mustermann", AccountType.GIRO, null);

        BankAccount updated = accountService.updateAccount(account.getId(), "Updated Bank", "NEWBIC", "New comment");

        assertEquals("Updated Bank", updated.getBankName());
        assertEquals("NEWBIC", updated.getBic());
        assertEquals("New comment", updated.getComment());
    }

    @Test
    void testDeleteAccount() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", "COBADEFFXXX", "Commerzbank",
                "Max Mustermann", AccountType.GIRO, null);

        accountService.deleteAccount(account.getId());

        assertTrue(accountService.getAllAccounts().isEmpty());
    }
}
