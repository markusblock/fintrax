package org.fintrax.fintx;

import org.fintrax.model.BankAccount;
import org.fintrax.model.Bank;
import org.fintrax.model.Transaction;

import java.util.List;
import java.util.function.Consumer;

public interface BankingProtocol {
    /** Looks up a local PIN/TAN endpoint from the account's German IBAN; returns null when absent. */
    default String lookupPinTanUrl(BankAccount account) throws BankingException {
        BankDirectoryEntry entry = lookupBankDetails(account.getIban());
        return entry == null ? null : entry.pinTanAddress();
    }

    /** Looks up bank identity and FinTS endpoint from the local bank directory without contacting the bank. */
    default BankDirectoryEntry lookupBankDetails(String germanIban) throws BankingException {
        return null;
    }

    /**
     * Fetches the complete transaction set for the bank-supported history window.
     * Implementations must throw rather than return partial or unmappable data.
     */
    List<Transaction> fetchTransactions(BankAccount account, String pin) throws BankingException;

    default List<Transaction> fetchTransactions(BankAccount account, Bank bank, String pin) throws BankingException {
        return fetchTransactions(account, pin);
    }

    boolean validatePin(BankAccount account, String pin) throws BankingException;

    default boolean validatePin(BankAccount account, Bank bank, String pin) throws BankingException {
        return validatePin(account, pin);
    }

    /** Reports non-sensitive progress while validating credentials; progress messages must never contain secrets. */
    default boolean validatePin(BankAccount account, Bank bank, String pin, Consumer<String> progress)
            throws BankingException {
        if (progress != null) {
            progress.accept("Validating bank credentials");
        }
        return validatePin(account, bank, pin);
    }

    /** Reads accounts exposed by the bank's FinTS passport; progress messages must not contain secrets. */
    default List<DiscoveredBankAccount> discoverAccounts(BankAccount account, Bank bank, String pin,
                                                          Consumer<String> progress) throws BankingException {
        throw new BankingException(BankingException.FailureReason.ACCOUNT_DISCOVERY_FAILED,
                "This banking protocol does not support account discovery.");
    }

    String getProtocolName();
}
