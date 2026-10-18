package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RuleEngine {
    private final StoreManager store;
    private final ActivityLogger activityLogger;

    public RuleEngine(StoreManager store, ActivityLogger activityLogger) {
        this.store = store;
        this.activityLogger = activityLogger;
    }

    public List<Transaction> applyRules(Transaction transaction) {
        TransactionRulePreview preview = previewTransaction(transaction, enabledRules(), false);
        if (!preview.matchedRuleNames().isEmpty()) {
            applyPreview(transaction, preview);
            store.store(transaction);
            log.debug("Rules matched transaction {}: {}", transaction.getId(), preview.matchedRuleNames());
        }
        return List.of(transaction);
    }

    public RuleApplicationPreview previewRules(boolean onlyFillMissing) {
        List<Rule> enabledRules = enabledRules();
        List<TransactionRulePreview> matchingTransactions = store.getRoot().getTransactions().stream()
                .map(transaction -> previewTransaction(transaction, enabledRules, onlyFillMissing))
                .filter(preview -> !preview.matchedRuleNames().isEmpty())
                .toList();
        return new RuleApplicationPreview(matchingTransactions);
    }

    public void applyReviewedRules(RuleApplicationPreview preview) {
        for (TransactionRulePreview reviewed : preview.transactionsWithChanges()) {
            Transaction transaction = store.getTransaction(reviewed.transactionId());
            if (transaction == null) {
                continue;
            }
            applyPreview(transaction, reviewed);
            transaction.setUpdatedAt(LocalDateTime.now());
            store.store(transaction);
        }
        log.info("Applied reviewed rules to {} transactions", preview.transactionsWithChanges().size());
    }

    private List<Rule> enabledRules() {
        return store.getRoot().getRules().stream()
                .filter(Rule::isEnabled)
                .sorted((first, second) -> Integer.compare(first.getPriority(), second.getPriority()))
                .toList();
    }

    private boolean matchesRule(Transaction transaction, Rule rule) {
        return rule.getConditions().stream()
                .allMatch(condition -> matchesCondition(transaction, condition));
    }

    private boolean matchesCondition(Transaction transaction, RuleCondition condition) {
        String value = getFieldValue(transaction, condition.getMatchField());

        return switch (condition.getOperator()) {
            case CONTAINS -> value != null && value.toLowerCase().contains(condition.getValue().toLowerCase());
            case REGEX -> value != null && Pattern.matches(condition.getValue(), value);
            case EQUALS -> value != null && value.equals(condition.getValue());
            case AMOUNT_RANGE -> {
                BigDecimal amount = transaction.getAmount();
                BigDecimal min = condition.getAmountMin();
                BigDecimal max = condition.getAmountMax();
                yield (min == null || amount.compareTo(min) >= 0) &&
                        (max == null || amount.compareTo(max) <= 0);
            }
        };
    }

    private String getFieldValue(Transaction transaction, MatchField field) {
        return switch (field) {
            case PAYEE_NAME -> transaction.getPayeeDisplay() != null ?
                    transaction.getPayeeDisplay() : transaction.getOriginalPayee();
            case PURPOSE -> transaction.getPurpose();
            case AMOUNT -> transaction.getAmount().toString();
            case ACCOUNT -> transaction.getAccountId().toString();
            case TRANSACTION_TYPE -> transaction.getTransactionType();
        };
    }

    private TransactionRulePreview previewTransaction(Transaction transaction, List<Rule> rules,
                                                       boolean onlyFillMissing) {
        List<Rule> matchedRules = rules.stream().filter(rule -> matchesRule(transaction, rule)).toList();
        Set<Long> currentLabels = transaction.getLabelIds() == null
                ? Set.of() : new HashSet<>(transaction.getLabelIds());
        if (matchedRules.isEmpty()) {
            return new TransactionRulePreview(transaction.getId(), transaction.getBookingDate(), payee(transaction),
                    transaction.getAmount(), transaction.getAccountId(), transaction.getCategoryId(),
                    transaction.getCategoryId(), currentLabels, currentLabels,
                    transaction.getNote(), transaction.getNote(), List.of());
        }

        Rule highestPriorityRule = matchedRules.get(0);
        RuleAction categoryAction = highestPriorityRule.getActions().stream()
                .filter(action -> action.getActionType() == ActionType.ASSIGN_CATEGORY)
                .findFirst().orElse(null);
        Long assignedCategoryId = categoryAction == null
                ? transaction.getCategoryId() : categoryAction.getCategoryId();
        if (onlyFillMissing && transaction.getCategoryId() != null) {
            assignedCategoryId = transaction.getCategoryId();
        }

        RuleAction noteAction = highestPriorityRule.getActions().stream()
                .filter(action -> action.getActionType() == ActionType.SET_NOTE)
                .findFirst().orElse(null);
        String proposedNote = noteAction == null ? transaction.getNote() : noteAction.getNote();

        Set<Long> proposedLabels = new HashSet<>(currentLabels);
        matchedRules.stream().flatMap(rule -> rule.getActions().stream())
                .filter(action -> action.getActionType() == ActionType.ADD_LABEL)
                .map(RuleAction::getLabelId)
                .filter(Objects::nonNull)
                .forEach(proposedLabels::add);

        return new TransactionRulePreview(transaction.getId(), transaction.getBookingDate(), payee(transaction),
                transaction.getAmount(), transaction.getAccountId(), transaction.getCategoryId(),
                assignedCategoryId, currentLabels, proposedLabels, transaction.getNote(), proposedNote,
                matchedRules.stream().map(Rule::getName).toList());
    }

    private String payee(Transaction transaction) {
        return transaction.getPayeeDisplay() != null
                ? transaction.getPayeeDisplay() : transaction.getOriginalPayee();
    }

    private void applyPreview(Transaction transaction, TransactionRulePreview preview) {
        transaction.setCategoryId(preview.proposedCategoryId());
        transaction.setLabelIds(new HashSet<>(preview.proposedLabelIds()));
        transaction.setNote(preview.proposedNote());
    }

    public List<Transaction> previewRule(Rule rule) {
        return store.getRoot().getTransactions().stream()
                .filter(transaction -> matchesRule(transaction, rule))
                .toList();
    }

    public int countMatchingTransactions(Rule rule) {
        return (int) store.getRoot().getTransactions().stream()
                .filter(transaction -> matchesRule(transaction, rule))
                .count();
    }

}
