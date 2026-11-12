package org.fintrax.fintx;

/** Account metadata returned by a bank's FinTS passport, before it is imported locally. */
public record DiscoveredBankAccount(
        String accountName,
        String bankType,
        String accountHolder,
        String iban,
        String bic,
        String bankCode,
        String accountNumber,
        String subAccountNumber,
        String currency) {
}
