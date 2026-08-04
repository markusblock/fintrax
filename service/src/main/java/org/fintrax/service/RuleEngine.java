package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        List<Rule> enabledRules = store.getRoot().getRules().stream()
                .filter(Rule::isEnabled)
                .sorted((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()))
                .toList();

        for (Rule rule : enabledRules) {
            if (matchesRule(transaction, rule)) {
                executeActions(transaction, rule);
                log.debug("Rule '{}' matched transaction {}", rule.getName(), transaction.getId());
                break;
            }
        }

        return List.of(transaction);
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

    private void executeActions(Transaction transaction, Rule rule) {
        for (RuleAction action : rule.getActions()) {
            switch (action.getActionType()) {
                case ASSIGN_CATEGORY -> transaction.setCategoryId(action.getCategoryId());
                case ADD_LABEL -> transaction.getLabelIds().add(action.getLabelId());
                case SET_NOTE -> transaction.setNote(action.getNote());
            }
        }
        store.store(transaction);
    }

    public List<Transaction> previewRule(Rule rule) {
        return store.getRoot().getTransactions().stream()
                .filter(t -> matchesRule(t, rule))
                .collect(Collectors.toList());
    }

    public int countMatchingTransactions(Rule rule) {
        return (int) store.getRoot().getTransactions().stream()
                .filter(t -> matchesRule(t, rule))
                .count();
    }

    public void applyAllRulesToAllTransactions() {
        List<Transaction> allTransactions = new ArrayList<>(store.getRoot().getTransactions());
        for (Transaction transaction : allTransactions) {
            applyRules(transaction);
        }
        log.info("Applied rules to {} transactions", allTransactions.size());
    }
}
