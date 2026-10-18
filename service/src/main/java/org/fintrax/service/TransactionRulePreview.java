package org.fintrax.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record TransactionRulePreview(
        Long transactionId,
        LocalDate bookingDate,
        String payee,
        BigDecimal amount,
        Long accountId,
        Long currentCategoryId,
        Long proposedCategoryId,
        Set<Long> currentLabelIds,
        Set<Long> proposedLabelIds,
        String currentNote,
        String proposedNote,
        List<String> matchedRuleNames) {

    public TransactionRulePreview {
        currentLabelIds = Set.copyOf(currentLabelIds);
        proposedLabelIds = Set.copyOf(proposedLabelIds);
        matchedRuleNames = List.copyOf(matchedRuleNames);
    }

    public boolean hasChanges() {
        return !java.util.Objects.equals(currentCategoryId, proposedCategoryId)
                || !currentLabelIds.equals(proposedLabelIds)
                || !java.util.Objects.equals(currentNote, proposedNote);
    }
}
