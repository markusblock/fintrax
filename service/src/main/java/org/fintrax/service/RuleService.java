package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RuleService {
    private final StoreManager store;
    private final ActivityLogger activityLogger;
    private final RuleEngine ruleEngine;
    private long nextId = 1;

    public RuleService(StoreManager store, ActivityLogger activityLogger, RuleEngine ruleEngine) {
        this.store = store;
        this.activityLogger = activityLogger;
        this.ruleEngine = ruleEngine;
        initializeNextId();
    }

    private void initializeNextId() {
        nextId = store.getRoot().getRules().stream()
                .mapToLong(Rule::getId)
                .max()
                .orElse(0) + 1;
    }

    public Rule createRule(String name, List<RuleCondition> conditions, List<RuleAction> actions) {
        validateRule(conditions, actions);

        int maxPriority = store.getRoot().getRules().stream()
                .mapToInt(Rule::getPriority)
                .max()
                .orElse(0);

        Rule rule = Rule.builder()
                .id(nextId++)
                .name(name)
                .priority(maxPriority + 1)
                .enabled(true)
                .conditions(conditions)
                .actions(actions)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        conditions.forEach(c -> c.setRuleId(rule.getId()));
        actions.forEach(a -> a.setRuleId(rule.getId()));

        store.getRoot().getRules().add(rule);
        store.store(store.getRoot().getRules());

        activityLogger.log(ActivityAction.CREATE, EntityType.RULE, rule.getId(),
                "Created rule: " + name);

        log.info("Created rule {} with name {}", rule.getId(), name);
        return rule;
    }

    private void validateRule(List<RuleCondition> conditions, List<RuleAction> actions) {
        long categoryActions = actions.stream()
                .filter(a -> a.getActionType() == ActionType.ASSIGN_CATEGORY)
                .count();
        if (categoryActions > 1) {
            throw new IllegalArgumentException("Rule can have at most 1 ASSIGN_CATEGORY action");
        }

        long noteActions = actions.stream()
                .filter(a -> a.getActionType() == ActionType.SET_NOTE)
                .count();
        if (noteActions > 1) {
            throw new IllegalArgumentException("Rule can have at most 1 SET_NOTE action");
        }
    }

    public Optional<Rule> getRule(Long id) {
        return Optional.ofNullable(store.getRule(id));
    }

    public List<Rule> getAllRules() {
        return store.getRoot().getRules().stream()
                .sorted((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()))
                .toList();
    }

    public Rule updateRule(Long id, String name, List<RuleCondition> conditions, List<RuleAction> actions) {
        Rule rule = store.getRule(id);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }

        validateRule(conditions, actions);

        rule.setName(name);
        rule.setConditions(conditions);
        rule.setActions(actions);
        rule.setUpdatedAt(LocalDateTime.now());

        conditions.forEach(c -> c.setRuleId(rule.getId()));
        actions.forEach(a -> a.setRuleId(rule.getId()));

        store.store(rule);

        activityLogger.log(ActivityAction.UPDATE, EntityType.RULE, id,
                "Updated rule: " + name);

        log.info("Updated rule {}", id);
        return rule;
    }

    public void deleteRule(Long id) {
        Rule rule = store.getRule(id);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }

        store.getRoot().getRules().remove(rule);
        store.store(store.getRoot().getRules());

        activityLogger.log(ActivityAction.DELETE, EntityType.RULE, id,
                "Deleted rule: " + rule.getName());

        log.info("Deleted rule {}", id);
    }

    public void toggleRule(Long id) {
        Rule rule = store.getRule(id);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }

        rule.setEnabled(!rule.isEnabled());
        rule.setUpdatedAt(LocalDateTime.now());

        store.store(rule);

        activityLogger.log(ActivityAction.UPDATE, EntityType.RULE, id,
                (rule.isEnabled() ? "Enabled" : "Disabled") + " rule: " + rule.getName());
    }

    public void reorderRules(List<Long> ruleIds) {
        for (int i = 0; i < ruleIds.size(); i++) {
            Rule rule = store.getRule(ruleIds.get(i));
            if (rule != null) {
                rule.setPriority(i + 1);
                store.store(rule);
            }
        }
        log.info("Reordered {} rules", ruleIds.size());
    }

    public int previewRule(Long ruleId) {
        Rule rule = store.getRule(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }
        return ruleEngine.countMatchingTransactions(rule);
    }

    public void applyAllRules() {
        ruleEngine.applyAllRulesToAllTransactions();
        activityLogger.log(ActivityAction.EXECUTE, EntityType.RULE, null,
                "Applied all rules to all transactions");
    }
}
