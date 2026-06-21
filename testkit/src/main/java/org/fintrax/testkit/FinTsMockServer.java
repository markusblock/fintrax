package org.fintrax.testkit;

import org.fintrax.fintx.BankingException;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinTsMockServer implements BankingProtocol {
    private final Map<String, String> pinStore = new HashMap<>();
    private final Map<String, List<Transaction>> transactionStore = new HashMap<>();

    public FinTsMockServer withPin(String iban, String pin) {
        pinStore.put(iban, pin);
        return this;
    }

    public FinTsMockServer withTransactions(String iban, List<Transaction> transactions) {
        transactionStore.put(iban, transactions);
        return this;
    }

    public FinTsMockServer withAccount(BankAccount account, String pin, List<Transaction> transactions) {
        pinStore.put(account.getIban(), pin);
        transactionStore.put(account.getIban(), transactions);
        return this;
    }

    @Override
    public List<Transaction> fetchTransactions(BankAccount account, String pin) throws BankingException {
        if (!validatePin(account, pin)) {
            throw new BankingException("Invalid PIN for account " + account.getIban());
        }

        List<Transaction> transactions = transactionStore.get(account.getIban());
        return transactions != null ? new ArrayList<>(transactions) : new ArrayList<>();
    }

    @Override
    public boolean validatePin(BankAccount account, String pin) throws BankingException {
        String storedPin = pinStore.get(account.getIban());
        if (storedPin == null) {
            throw new BankingException("Account not found: " + account.getIban());
        }
        return storedPin.equals(pin);
    }

    @Override
    public String getProtocolName() {
        return "FinTS Mock";
    }

    public static Transaction createMockTransaction(Long accountId, String payee, BigDecimal amount, LocalDate date) {
        return Transaction.builder()
                .accountId(accountId)
                .originalPayee(payee)
                .purpose("Test transaction")
                .amount(amount)
                .bookingDate(date)
                .valueDate(date)
                .checksum("mock_" + payee + "_" + amount + "_" + date)
                .booked(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
