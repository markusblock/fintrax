package org.fintrax.ui.control;

import org.fintrax.model.BankAccount;

public final class AccountDisplay {
    private static final int MIN_MASKED_IBAN_LENGTH = 8;
    private static final int IBAN_EDGE_LENGTH = 4;

    private AccountDisplay() {
    }

    public static String name(BankAccount account) {
        if (account == null) {
            return "";
        }
        String bankName = account.getBankName() == null ? "" : account.getBankName().trim();
        String iban = account.getIban() == null ? "" : account.getIban().trim();
        String maskedIban = iban.length() < MIN_MASKED_IBAN_LENGTH ? iban
                : iban.substring(0, IBAN_EDGE_LENGTH) + "****"
                        + iban.substring(iban.length() - IBAN_EDGE_LENGTH);
        if (bankName.isBlank() && maskedIban.isBlank()) {
            return "";
        }
        if (bankName.isBlank()) {
            return maskedIban;
        }
        if (maskedIban.isBlank()) {
            return bankName;
        }
        return bankName + " (" + maskedIban + ")";
    }
}