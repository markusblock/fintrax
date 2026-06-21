package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
public class AccountService {
    private final StoreManager store;
    private final ActivityLogger activityLogger;
    private long nextId = 1;

    public AccountService(StoreManager store, ActivityLogger activityLogger) {
        this.store = store;
        this.activityLogger = activityLogger;
        initializeNextId();
    }

    private void initializeNextId() {
        nextId = store.getRoot().getAccounts().stream()
                .mapToLong(BankAccount::getId)
                .max()
                .orElse(0) + 1;
    }

    public BankAccount createAccount(String iban, String bic, String bankName, String accountHolder,
                                     AccountType accountType, String comment) {
        BankAccount account = BankAccount.builder()
                .id(nextId++)
                .iban(iban)
                .bic(bic)
                .bankName(bankName)
                .accountHolder(accountHolder)
                .accountType(accountType)
                .comment(comment)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        store.getRoot().getAccounts().add(account);
        store.store(store.getRoot().getAccounts());

        activityLogger.log(ActivityAction.CREATE, EntityType.ACCOUNT, account.getId(),
                "Created account: " + bankName + " (" + iban + ")");

        log.info("Created account {} with IBAN {}", account.getId(), iban);
        return account;
    }

    public Optional<BankAccount> getAccount(Long id) {
        return Optional.ofNullable(store.getAccount(id));
    }

    public List<BankAccount> getAllAccounts() {
        return List.copyOf(store.getRoot().getAccounts());
    }

    public BankAccount updateAccount(Long id, String bankName, String bic, String comment) {
        BankAccount account = store.getAccount(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        account.setBankName(bankName);
        account.setBic(bic);
        account.setComment(comment);
        account.setUpdatedAt(LocalDateTime.now());

        store.store(account);

        activityLogger.log(ActivityAction.UPDATE, EntityType.ACCOUNT, id,
                "Updated account: " + bankName);

        log.info("Updated account {}", id);
        return account;
    }

    public void deleteAccount(Long id) {
        BankAccount account = store.getAccount(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        store.getRoot().getAccounts().remove(account);
        store.getRoot().getTransactions().removeIf(t -> t.getAccountId().equals(id));
        store.store(store.getRoot().getAccounts());
        store.store(store.getRoot().getTransactions());

        activityLogger.log(ActivityAction.DELETE, EntityType.ACCOUNT, id,
                "Deleted account: " + account.getBankName());

        log.info("Deleted account {} and its transactions", id);
    }

    public void updateBalance(Long id, java.math.BigDecimal balance, LocalDateTime balanceDate) {
        BankAccount account = store.getAccount(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        account.setBalance(balance);
        account.setBalanceDate(balanceDate);
        account.setUpdatedAt(LocalDateTime.now());

        store.store(account);
    }

    public void updateLastSync(Long id, LocalDateTime syncTime) {
        BankAccount account = store.getAccount(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        account.setLastSyncAt(syncTime);
        account.setUpdatedAt(LocalDateTime.now());

        store.store(account);
    }
}
