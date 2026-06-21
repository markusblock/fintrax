package org.fintrax.fintx;

import org.fintrax.model.BankAccount;
import org.fintrax.model.Transaction;

import java.util.List;

public interface BankingProtocol {
    List<Transaction> fetchTransactions(BankAccount account, String pin) throws BankingException;

    boolean validatePin(BankAccount account, String pin) throws BankingException;

    String getProtocolName();
}
