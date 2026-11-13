package org.fintrax.fintx;

import org.fintrax.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BankingSyncData(List<Transaction> transactions, BigDecimal accountBalance,
                              LocalDateTime balanceDate) {
    public BankingSyncData {
        transactions = List.copyOf(transactions);
    }
}
