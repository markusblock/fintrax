package org.fintrax.service;

import org.fintrax.fintx.BankDirectoryEntry;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.model.AccountType;
import org.fintrax.model.Bank;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Transaction;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BankServiceTest {
    private Path storePath;
    private StoreManager store;

    @AfterEach
    void tearDown() throws Exception {
        if (store != null) {
            store.shutdown();
        }
        if (storePath != null) {
            try (var paths = Files.walk(storePath)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    void migratesAccountsWithSameBlzToOneBankWithoutTreatingHolderAsLogin() throws Exception {
        storePath = Files.createTempDirectory("fintrax-bank-profile-test");
        store = new StoreManager(storePath);
        BankAccount extraAccount = legacyAccount(1L, "DE54500105175403021161", "Extra-Konto");
        BankAccount currentAccount = legacyAccount(2L, "DE05500105175403021161", "Girokonto");
        store.getRoot().getAccounts().addAll(List.of(extraAccount, currentAccount));
        store.store(store.getRoot().getAccounts());

        BankService bankService = new BankService(store, new DirectoryProtocol());

        Bank extraBank = bankService.getBankForAccount(extraAccount).orElseThrow();
        Bank currentBank = bankService.getBankForAccount(currentAccount).orElseThrow();
        assertSame(extraBank, currentBank);
        assertEquals("50010517", extraBank.getBlz());
        assertEquals("ING-DiBa", extraBank.getName());
        assertEquals("Extra-Konto", extraAccount.getAccountName());
        assertEquals("Girokonto", currentAccount.getAccountName());
        assertEquals("BLOCK, MARKUS", extraAccount.getAccountHolder());
        assertNull(extraBank.getFintsUserId());
    }

    private BankAccount legacyAccount(Long id, String iban, String oldAccountName) {
        return BankAccount.builder()
                .id(id)
                .iban(iban)
                .bic("INGDDEFFXXX")
                .bankName(oldAccountName)
                .accountHolder("BLOCK, MARKUS")
                .accountType(AccountType.GIRO)
                .build();
    }

    private static final class DirectoryProtocol implements BankingProtocol {
        @Override
        public BankDirectoryEntry lookupBankDetails(String germanIban) {
            return new BankDirectoryEntry("50010517", "ING-DiBa", "INGDDEFFXXX",
                    "https://fints.ing.de/fints/");
        }

        @Override
        public List<Transaction> fetchTransactions(BankAccount account, String pin) {
            return List.of();
        }

        @Override
        public boolean validatePin(BankAccount account, String pin) {
            return true;
        }

        @Override
        public String getProtocolName() {
            return "FinTS test double";
        }
    }
}
