package org.fintrax.ui.controller;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks newly imported transactions that have not yet been viewed during this application session. */
@Component
public class NewTransactionState {
    private final Set<Long> unviewedIds = ConcurrentHashMap.newKeySet();

    public void markNew(Collection<Long> transactionIds) {
        if (transactionIds != null) {
            transactionIds.stream().filter(id -> id != null).forEach(unviewedIds::add);
        }
    }

    public boolean isUnviewedNew(Long transactionId) {
        return transactionId != null && unviewedIds.contains(transactionId);
    }

    public boolean markViewed(Long transactionId) {
        return transactionId != null && unviewedIds.remove(transactionId);
    }
}
