package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AccountService {
    private final StoreManager store;
    private final ActivityLogger activityLogger;
    private final BankService bankService;
    private long nextId = 1;

    @Autowired
    public AccountService(StoreManager store, ActivityLogger activityLogger, BankService bankService) {
        this.store = store;
        this.activityLogger = activityLogger;
        this.bankService = bankService;
        initializeNextId();
    }

    public AccountService(StoreManager store, ActivityLogger activityLogger) {
        this(store, activityLogger, null);
    }

    private void initializeNextId() {
        nextId = store.getRoot().getAccounts().stream()
                .mapToLong(BankAccount::getId)
                .max()
                .orElse(0) + 1;
    }

    public BankAccount createAccount(String iban, String bic, String bankName, String accountHolder,
                                     AccountType accountType, String comment) {
        return createAccount(iban, bic, null, bankName, accountHolder, accountType, comment);
    }

    public BankAccount createAccount(String iban, String bic, String fintsServerUrl,
                                     String bankName, String accountHolder,
                                     AccountType accountType, String comment) {
        Bank bank = bankService == null ? null
                : bankService.findOrCreateLegacy(iban, bic, fintsServerUrl, bankName);
        BankAccount account = BankAccount.builder()
                .id(nextId++)
                .bankId(bank == null ? null : bank.getId())
                .iban(iban)
                .accountName(bankName)
                .bic(bic)
                .fintsServerUrl(fintsServerUrl)
                .bankName(bankName)
                .accountHolder(accountHolder)
                .accountType(accountType)
                .comment(comment)
                .createdAt(LocalDateTime.now(ZoneId.systemDefault()))
                .updatedAt(LocalDateTime.now(ZoneId.systemDefault()))
                .build();

        store.getRoot().getAccounts().add(account);
        store.store(store.getRoot().getAccounts());

        activityLogger.log(ActivityAction.CREATE, EntityType.ACCOUNT, account.getId(),
                "Created bank account");

        log.info("Created bank account {}", account.getId());
        return account;
    }

    public BankAccount createAccount(Long bankId, String iban, String accountName, String accountHolder,
                                     AccountType accountType, String currency, String comment) {
        BankAccount account = BankAccount.builder()
                .id(nextId++)
                .bankId(bankId)
                .iban(iban)
                .accountName(accountName)
                .accountHolder(accountHolder)
                .accountType(accountType)
                .currency(currency == null || currency.isBlank() ? "EUR" : currency.trim())
                .comment(comment)
                .createdAt(LocalDateTime.now(ZoneId.systemDefault()))
                .updatedAt(LocalDateTime.now(ZoneId.systemDefault()))
                .build();
        store.getRoot().getAccounts().add(account);
        store.store(store.getRoot().getAccounts());
        activityLogger.log(ActivityAction.CREATE, EntityType.ACCOUNT, account.getId(), "Created bank account");
        return account;
    }

    public Optional<BankAccount> getAccount(Long id) {
        return Optional.ofNullable(store.getAccount(id));
    }

    public List<BankAccount> getAllAccounts() {
        return List.copyOf(store.getRoot().getAccounts());
    }

    public BankAccount updateAccount(Long id, String bankName, String bic, String accountHolder,
                                     AccountType accountType, String comment) {
        BankAccount account = store.getAccount(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        return updateAccount(id, bankName, bic, account.getFintsServerUrl(), accountHolder, accountType, comment);
    }

    public BankAccount updateAccount(Long id, String bankName, String bic, String fintsServerUrl,
                                     String accountHolder, AccountType accountType, String comment) {
        BankAccount account = store.getAccount(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        account.setBankName(bankName);
        account.setAccountName(bankName);
        account.setBic(bic);
        account.setFintsServerUrl(fintsServerUrl);
        account.setAccountHolder(accountHolder);
        account.setAccountType(accountType);
        account.setComment(comment);
        account.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));

        if (bankService != null && account.getBankId() != null) {
            Bank bank = bankService.getBank(account.getBankId()).orElse(null);
            if (bank != null) {
                bankService.updateBank(bank.getId(), bankName, bic, fintsServerUrl,
                        bank.getFintsUserId(), bank.getFintsCustomerId(), bank.getComment());
            }
        }

        store.store(account);

        activityLogger.log(ActivityAction.UPDATE, EntityType.ACCOUNT, id,
                "Updated account: " + bankName);

        log.info("Updated account {}", id);
        return account;
    }

    public BankAccount updateAccount(Long id, String accountName, String accountHolder,
                                     AccountType accountType, String currency, String comment) {
        BankAccount account = store.getAccount(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        return updateAccount(id, account.getBankId(), accountName, accountHolder,
                accountType, currency, comment);
    }

    public BankAccount updateAccount(Long id, Long bankId, String accountName, String accountHolder,
                                     AccountType accountType, String currency, String comment) {
        BankAccount account = store.getAccount(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        account.setBankId(bankId);
        account.setAccountName(accountName);
        account.setAccountHolder(accountHolder);
        account.setAccountType(accountType);
        account.setCurrency(currency == null || currency.isBlank() ? "EUR" : currency.trim());
        account.setComment(comment);
        account.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        store.store(account);
        activityLogger.log(ActivityAction.UPDATE, EntityType.ACCOUNT, id, "Updated bank account");
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
        if (bankService != null) {
            bankService.deleteIfUnused(account.getBankId());
        }

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
        account.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));

        store.store(account);
    }

    public void updateLastSync(Long id, LocalDateTime syncTime) {
        BankAccount account = store.getAccount(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        account.setLastSyncAt(syncTime);
        account.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));

        store.store(account);
    }
}
