package org.fintrax.fintx;

public class BankingException extends Exception {
    public static final String TAN_REQUIRED_MESSAGE =
            "This bank sync requires TAN authentication, which is not available. No TAN was sent.";

    public BankingException(String message) {
        super(message);
    }

    public BankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
