package org.fintrax.ui.controller;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped set of transaction IDs whose details have been viewed (selected in the table).
 * Visible in all transaction tables: TransactionsView, RulesView, ClassificationsView.
 * Cleared on every table reload so newly imported / newly visible transactions appear bold again.
 * Not persisted to disk — resets on application restart.
 */
@Component
public class SettledTransactionIds {
    private final Set<Long> settled = ConcurrentHashMap.newKeySet();

    public boolean isSettled(Long id) {
        return id != null && settled.contains(id);
    }

    public void settle(Long id) {
        if (id != null) {
            settled.add(id);
        }
    }

    public void clear() {
        settled.clear();
    }

    public int size() {
        return settled.size();
    }
}
