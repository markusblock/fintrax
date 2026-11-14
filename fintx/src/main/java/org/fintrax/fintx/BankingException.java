package org.fintrax.fintx;

import org.fintrax.model.SyncStage;

public class BankingException extends Exception {
    public static final String TAN_REQUIRED_MESSAGE =
            "This bank sync requires TAN authentication, which is not available. No TAN was sent.";

    private final FailureReason failureReason;
    private final SyncStage syncStage;

    public BankingException(String message) {
        this(FailureReason.UNKNOWN, message, null);
    }

    public BankingException(String message, Throwable cause) {
        this(FailureReason.UNKNOWN, message, cause);
    }

    public BankingException(FailureReason failureReason, String message) {
        this(failureReason, message, null);
    }

    public BankingException(FailureReason failureReason, SyncStage syncStage, String message) {
        this(failureReason, syncStage, message, null);
    }

    public BankingException(FailureReason failureReason, String message, Throwable cause) {
        this(failureReason, null, message, cause);
    }

    public BankingException(FailureReason failureReason, SyncStage syncStage, String message, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
        this.syncStage = syncStage;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public SyncStage getSyncStage() {
        return syncStage;
    }

    public enum FailureReason {
        TAN_REQUIRED,
        BANK_REJECTED,
        INCOMPLETE_RESPONSE,
        MAPPING_FAILED,
        CONNECTION_FAILED,
        CREDENTIAL_VALIDATION_FAILED,
        ACCOUNT_MISMATCH,
        INVALID_IBAN,
        MISSING_ENDPOINT,
        INVALID_ENDPOINT,
        BANK_DIRECTORY_LOOKUP_FAILED,
        ACCOUNT_DISCOVERY_FAILED,
        MISSING_BANK_PROFILE,
        MISSING_USER_ID,
        UNKNOWN
    }
}
