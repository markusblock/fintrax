package org.fintrax.service;

import java.util.List;

public record RuleApplicationPreview(List<TransactionRulePreview> matchingTransactions) {
    public RuleApplicationPreview {
        matchingTransactions = List.copyOf(matchingTransactions);
    }

    public List<TransactionRulePreview> transactionsWithChanges() {
        return matchingTransactions.stream().filter(TransactionRulePreview::hasChanges).toList();
    }

    public long matchingTransactionsWithoutChanges() {
        return matchingTransactions.stream().filter(preview -> !preview.hasChanges()).count();
    }
}
