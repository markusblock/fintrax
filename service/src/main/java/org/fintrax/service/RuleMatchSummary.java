package org.fintrax.service;

import java.util.List;

public record RuleMatchSummary(int matchingTransactionCount, int conflictingTransactionCount,
                               List<MatchingRule> conflictingRules) {
    public RuleMatchSummary {
        conflictingRules = List.copyOf(conflictingRules);
    }

    public record MatchingRule(String name, int priority) {}
}
