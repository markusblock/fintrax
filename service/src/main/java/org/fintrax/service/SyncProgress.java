package org.fintrax.service;

import org.fintrax.model.SyncStage;

public record SyncProgress(Long accountId, SyncStage stage, int transactionsDownloaded,
                           int transactionsImported, int transactionsAlreadyKnown) {
}
