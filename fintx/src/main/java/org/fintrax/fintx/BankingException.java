package org.fintrax.fintx;

public class BankingException extends Exception {
    public static final String TAN_REQUIRED_MESSAGE =
            "This bank sync requires TAN authentication, which is not available. No TAN was sent.";

    private final FailureReason failureReason;

    public BankingException(String message) {
        this(FailureReason.UNKNOWN, message, null);
    }

    public BankingException(String message, Throwable cause) {
        this(FailureReason.UNKNOWN, message, cause);
    }

    public BankingException(FailureReason failureReason, String message) {
        this(failureReason, message, null);
    }

    public BankingException(FailureReason failureReason, String message, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public enum FailureReason {
        TAN_REQUIRED,
        BANK_REJECTED,
        INCOMPLETE_RESPONSE,
        MAPPING_FAILED,
        CONNECTION_FAILED,
        CREDENTIAL_VALIDATION_FAILED,
        ACCOUNT_MISMATCH,
        UNKNOWN
    }
}
