package org.fintrax.service;

import org.fintrax.model.SyncLog;

import java.util.List;

public record SyncResult(SyncLog syncLog, List<Long> newTransactionIds) {
    public SyncResult {
        newTransactionIds = List.copyOf(newTransactionIds);
    }
}
