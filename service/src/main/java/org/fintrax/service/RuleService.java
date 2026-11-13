package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
        return createRule(name, conditions, actions, RuleMatchMode.ALL);
    }

    public Rule createRule(String name, List<RuleCondition> conditions, List<RuleAction> actions,
                           RuleMatchMode matchMode) {
        validateUniqueName(name, null);
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
                .matchMode(matchMode == null ? RuleMatchMode.ALL : matchMode)
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
        if (conditions == null || actions == null
                || conditions.stream().anyMatch(Objects::isNull)
                || actions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Rule conditions and actions are required");
        }
        conditions.stream()
                .filter(condition -> condition.getOperator() == Operator.REGEX
                        || condition.getOperator() == Operator.NOT_REGEX)
                .forEach(condition -> {
                    if (condition.getValue() == null) {
                        throw new IllegalArgumentException("Regular expression is required for rule condition");
                    }
                    try {
                        Pattern.compile(condition.getValue());
                    } catch (PatternSyntaxException exception) {
                        throw new IllegalArgumentException("Invalid regular expression in rule condition", exception);
                    }
                });
        long categoryActions = actions.stream()
                .filter(a -> a.getActionType() == ActionType.ASSIGN_CATEGORY)
                .count();
        if (categoryActions > 1) {
            throw new IllegalArgumentException("Rule can have at most 1 ASSIGN_CATEGORY action");
        }

        if (actions.stream().anyMatch(action -> action.getActionType() == ActionType.SET_NOTE)) {
            throw new IllegalArgumentException("Rules cannot set transaction notes");
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
        Rule existing = store.getRule(id);
        RuleMatchMode matchMode = existing == null ? RuleMatchMode.ALL : existing.getMatchMode();
        return updateRule(id, name, conditions, actions, matchMode);
    }

    public Rule updateRule(Long id, String name, List<RuleCondition> conditions, List<RuleAction> actions,
                           RuleMatchMode matchMode) {
        Rule rule = store.getRule(id);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }

        validateUniqueName(name, id);
        validateRule(conditions, actions);

        rule.setName(name);
        rule.setConditions(conditions);
        rule.setMatchMode(matchMode == null ? RuleMatchMode.ALL : matchMode);
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

    /** Merges imported rules by case-insensitive name without deleting rules absent from the import. */
    public MergeResult mergeImportedRules(List<Rule> importedRules) {
        validateImportedRuleList(importedRules);
        MergePlan plan = createMergePlan(importedRules);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        applyImportedUpdates(plan.updates(), now);
        applyImportedAdditions(plan.additions(), now);
        if (!plan.updates().isEmpty() || !plan.additions().isEmpty()) {
            store.store(store.getRoot().getRules());
        }
        return new MergeResult(plan.additions().size(), plan.updates().size());
    }

    private void validateImportedRuleList(List<Rule> importedRules) {
        if (importedRules == null || importedRules.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Imported rules are required");
        }
    }

    private MergePlan createMergePlan(List<Rule> importedRules) {
        Map<String, List<Rule>> existingByName = new HashMap<>();
        store.getRoot().getRules().forEach(rule -> existingByName
                .computeIfAbsent(normalizeName(rule.getName()), ignored -> new ArrayList<>()).add(rule));

        Set<String> importedNames = new HashSet<>();
        List<RuleUpdate> updates = new ArrayList<>();
        List<Rule> additions = new ArrayList<>();
        for (Rule imported : importedRules) {
            validateImportedRule(imported);
            String key = normalizeName(imported.getName());
            if (!importedNames.add(key)) {
                throw new IllegalArgumentException("Duplicate imported rule name: " + imported.getName());
            }
            addImportedRule(imported, existingByName.getOrDefault(key, List.of()), updates, additions);
        }
        return new MergePlan(updates, additions);
    }

    private void validateImportedRule(Rule imported) {
        if (imported.getName() == null || imported.getName().isBlank()) {
            throw new IllegalArgumentException("Rule name is required");
        }
        validateRule(imported.getConditions(), imported.getActions());
    }

    private void addImportedRule(Rule imported, List<Rule> matches,
                                 List<RuleUpdate> updates, List<Rule> additions) {
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Multiple existing rules have the name: " + imported.getName());
        }
        if (matches.isEmpty()) {
            additions.add(imported);
        } else if (!hasSameImportedValues(matches.getFirst(), imported)) {
            updates.add(new RuleUpdate(matches.getFirst(), imported));
        }
    }

    private boolean hasSameImportedValues(Rule existing, Rule imported) {
        return Objects.equals(existing.getName(), imported.getName())
                && existing.getPriority() == imported.getPriority()
                && existing.isEnabled() == imported.isEnabled()
                && effectiveMatchMode(existing) == effectiveMatchMode(imported)
                && hasSameConditionValues(existing.getConditions(), imported.getConditions())
                && hasSameActionValues(existing.getActions(), imported.getActions());
    }

    private RuleMatchMode effectiveMatchMode(Rule rule) {
        return rule.getMatchMode() == null ? RuleMatchMode.ALL : rule.getMatchMode();
    }

    private boolean hasSameConditionValues(List<RuleCondition> existing, List<RuleCondition> imported) {
        if (existing == null || existing.size() != imported.size()) return false;
        for (int index = 0; index < existing.size(); index++) {
            RuleCondition current = existing.get(index);
            RuleCondition incoming = imported.get(index);
            if (!Objects.equals(current.getMatchField(), incoming.getMatchField())
                    || !Objects.equals(current.getOperator(), incoming.getOperator())
                    || !Objects.equals(current.getValue(), incoming.getValue())
                    || !sameAmountValue(current.getAmountMin(), incoming.getAmountMin())
                    || !sameAmountValue(current.getAmountMax(), incoming.getAmountMax())) {
                return false;
            }
        }
        return true;
    }

    private boolean sameAmountValue(BigDecimal existing, BigDecimal imported) {
        return existing == null ? imported == null : imported != null && existing.compareTo(imported) == 0;
    }

    private boolean hasSameActionValues(List<RuleAction> existing, List<RuleAction> imported) {
        if (existing == null || existing.size() != imported.size()) return false;
        for (int index = 0; index < existing.size(); index++) {
            RuleAction current = existing.get(index);
            RuleAction incoming = imported.get(index);
            if (current.getActionType() != incoming.getActionType()
                    || !Objects.equals(current.getCategoryId(), incoming.getCategoryId())
                    || !Objects.equals(current.getLabelId(), incoming.getLabelId())) {
                return false;
            }
        }
        return true;
    }

    private void applyImportedUpdates(List<RuleUpdate> updates, LocalDateTime now) {
        for (RuleUpdate update : updates) {
            Rule existing = update.existing();
            Rule imported = update.imported();
            applyImportedValues(existing, imported, now);
            activityLogger.log(ActivityAction.UPDATE, EntityType.RULE, existing.getId(),
                    "Updated imported rule: " + existing.getName());
        }
    }

    private void applyImportedAdditions(List<Rule> additions, LocalDateTime now) {
        for (Rule imported : additions) {
            imported.setId(nextId++);
            if (imported.getMatchMode() == null) imported.setMatchMode(RuleMatchMode.ALL);
            imported.setCreatedAt(now);
            imported.setUpdatedAt(now);
            setChildRuleIds(imported);
            store.getRoot().getRules().add(imported);
            activityLogger.log(ActivityAction.CREATE, EntityType.RULE, imported.getId(),
                    "Imported rule: " + imported.getName());
        }
    }

    private void applyImportedValues(Rule existing, Rule imported, LocalDateTime now) {
        existing.setName(imported.getName());
        existing.setPriority(imported.getPriority());
        existing.setEnabled(imported.isEnabled());
        existing.setConditions(imported.getConditions());
        existing.setMatchMode(imported.getMatchMode() == null ? RuleMatchMode.ALL : imported.getMatchMode());
        existing.setActions(imported.getActions());
        existing.setUpdatedAt(now);
        setChildRuleIds(existing);
    }

    private void setChildRuleIds(Rule rule) {
        rule.getConditions().forEach(condition -> condition.setRuleId(rule.getId()));
        rule.getActions().forEach(action -> action.setRuleId(rule.getId()));
    }

    private void validateUniqueName(String name, Long exceptId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Rule name is required");
        }
        if (!isRuleNameAvailable(name, exceptId)) {
            throw new IllegalArgumentException("Rule with name already exists: " + name);
        }
    }

    public boolean isRuleNameAvailable(String name, Long exceptId) {
        if (name == null || name.isBlank()) return false;
        return store.getRoot().getRules().stream()
                .noneMatch(rule -> (exceptId == null || !Objects.equals(rule.getId(), exceptId))
                        && rule.getName() != null && rule.getName().equalsIgnoreCase(name));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public record MergeResult(int added, int updated) {}

    private record MergePlan(List<RuleUpdate> updates, List<Rule> additions) {}

    private record RuleUpdate(Rule existing, Rule imported) {}

    void validateRuleForImport(List<RuleCondition> conditions, List<RuleAction> actions) {
        validateRule(conditions, actions);
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

    public RuleMatchSummary getMatchSummary(Long ruleId) {
        Rule rule = store.getRule(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }
        return ruleEngine.getMatchSummary(rule);
    }

    public Map<Long, List<RuleMatchSummary.MatchingRule>> getMatchingRulesForTransactions(List<Long> transactionIds) {
        return ruleEngine.getMatchingRulesForTransactions(transactionIds);
    }

    public List<Transaction> getMatchingTransactions(Long ruleId) {
        Rule rule = store.getRule(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }
        return ruleEngine.previewRule(rule);
    }

    public RuleApplicationPreview previewAllRules(boolean onlyFillMissing) {
        return ruleEngine.previewRules(onlyFillMissing);
    }

    public RuleApplicationPreview previewSingleRule(Long ruleId, boolean onlyFillMissing) {
        return ruleEngine.previewSingleRule(ruleId, onlyFillMissing);
    }

    public RuleApplicationPreview previewRulesForTransactions(List<Long> transactionIds, boolean onlyFillMissing) {
        return ruleEngine.previewRulesForTransactions(transactionIds, onlyFillMissing);
    }

    public void applyReviewedRules(RuleApplicationPreview preview) {
        ruleEngine.applyReviewedRules(preview);
        activityLogger.log(ActivityAction.EXECUTE, EntityType.RULE, null,
                "Applied reviewed rules to " + preview.transactionsWithChanges().size() + " transactions");
    }
}
