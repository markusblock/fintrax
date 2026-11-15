package org.fintrax.service;

import org.fintrax.model.SyncLog;

import java.util.List;

public record SyncResult(SyncLog syncLog, List<Long> newTransactionIds, boolean authenticationFailed) {
    public SyncResult(SyncLog syncLog, List<Long> newTransactionIds) {
        this(syncLog, newTransactionIds, false);
    }

    public SyncResult {
        newTransactionIds = List.copyOf(newTransactionIds);
    }
}
