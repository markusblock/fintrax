package org.fintrax.fintx;

import org.fintrax.model.BankAccount;
import org.fintrax.model.Transaction;

import java.util.List;

public interface BankingProtocol {
    /**
     * Fetches the complete transaction set for the bank-supported history window.
     * Implementations must throw rather than return partial or unmappable data.
     */
    List<Transaction> fetchTransactions(BankAccount account, String pin) throws BankingException;

    boolean validatePin(BankAccount account, String pin) throws BankingException;

    String getProtocolName();
}
