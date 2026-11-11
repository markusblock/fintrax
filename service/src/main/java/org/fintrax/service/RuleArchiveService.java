package org.fintrax.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fintrax.model.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class RuleArchiveService {
    private static final String ARCHIVE_FORMAT = "fintrax-export";
    private static final String PAYLOAD_NAME = "rules.json";
    private static final int SCHEMA_VERSION = 1;
    private static final String PARTIAL_SCOPE = "PARTIAL";
    private static final int MAX_ARCHIVE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 20 * 1024 * 1024;

    private final RuleService ruleService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RuleArchiveService(RuleService ruleService, AccountService accountService,
                              CategoryService categoryService, LabelService labelService) {
        this.ruleService = ruleService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
    }

    /** Writes every rule and its portable references to a versioned ZIP archive. */
    public void exportRules(Path destination) throws IOException {
        if (destination == null) throw new IllegalArgumentException("Export destination is required");
        List<Rule> rules = ruleService.getAllRules();
        validateUniqueRuleNames(rules);
        List<ExportedRule> exportedRules = rules.stream().map(this::exportRule).toList();
        ExportData data = new ExportData(exportedRules);
        byte[] json = objectMapper.writeValueAsBytes(
                new ExportDocument(ARCHIVE_FORMAT, SCHEMA_VERSION, PARTIAL_SCOPE, data));

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination))) {
            zip.putNextEntry(new ZipEntry(PAYLOAD_NAME));
            zip.write(json);
            zip.closeEntry();
        }
    }

    /** Imports a rules archive as a merge; local rules absent from the archive remain unchanged. */
    public ImportResult importRules(Path archivePath) throws IOException {
        if (archivePath == null) throw new IllegalArgumentException("Rules archive is required");
        if (Files.size(archivePath) > MAX_ARCHIVE_BYTES) {
            throw new IllegalArgumentException("Rules archive exceeds the maximum file size");
        }

        ExportDocument archive = readArchive(archivePath);
        if (!ARCHIVE_FORMAT.equals(archive.format())) {
            throw new IllegalArgumentException("This ZIP file is not a Fintrax export");
        }
        if (archive.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported rules export schema version: " + archive.schemaVersion());
        }
        if (!PARTIAL_SCOPE.equals(archive.scope())) {
            throw new IllegalArgumentException("Only partial rules exports can be imported here");
        }
        if (archive.data() == null || archive.data().rules() == null) {
            throw new IllegalArgumentException("Rules export has no rules list");
        }

        List<Rule> importedRules = archive.data().rules().stream().map(this::importRule).toList();
        RuleService.MergeResult result = ruleService.mergeImportedRules(importedRules);
        return new ImportResult(result.added(), result.updated());
    }

    private ExportDocument readArchive(Path archivePath) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archivePath))) {
            ZipEntry entry = zip.getNextEntry();
            if (entry == null || entry.isDirectory() || !PAYLOAD_NAME.equals(entry.getName())) {
                throw new IllegalArgumentException("Rules archive must contain a rules.json entry");
            }
            if (entry.getSize() > MAX_JSON_BYTES) {
                throw new IllegalArgumentException("Rules export payload exceeds the maximum size");
            }
            byte[] json = zip.readNBytes(MAX_JSON_BYTES + 1);
            if (json.length > MAX_JSON_BYTES) {
                throw new IllegalArgumentException("Rules export payload exceeds the maximum size");
            }
            if (zip.getNextEntry() != null) {
                throw new IllegalArgumentException("Rules archive must contain exactly one payload file");
            }
            return objectMapper.readValue(json, ExportDocument.class);
        }
    }

    private ExportedRule exportRule(Rule rule) {
        ruleService.validateRuleForImport(rule.getConditions(), rule.getActions());
        return new ExportedRule(rule.getName(), rule.getPriority(), rule.isEnabled(),
                rule.getMatchMode() == null ? RuleMatchMode.ALL : rule.getMatchMode(),
                rule.getConditions().stream().map(condition -> exportCondition(rule, condition)).toList(),
                rule.getActions().stream().map(this::exportAction).toList());
    }

    private ExportedCondition exportCondition(Rule rule, RuleCondition condition) {
        if (condition.getMatchField() == null || condition.getOperator() == null) {
            throw new IllegalArgumentException("Rule '" + rule.getName() + "' has an incomplete condition");
        }
        if (condition.getMatchField() != MatchField.ACCOUNT) {
            return new ExportedCondition(condition.getMatchField(), condition.getOperator(), condition.getValue(),
                    condition.getAmountMin(), condition.getAmountMax(), null);
        }
        if (condition.getOperator() != Operator.EQUALS && condition.getOperator() != Operator.NOT_EQUALS) {
            throw new IllegalArgumentException("Rule '" + rule.getName()
                    + "' uses an account condition that cannot be exported portably");
        }

        Long accountId;
        try {
            accountId = Long.valueOf(condition.getValue());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Rule '" + rule.getName()
                    + "' has an invalid account reference", exception);
        }
        if (condition.getBankAccountId() != null && !condition.getBankAccountId().equals(accountId)) {
            throw new IllegalArgumentException("Rule '" + rule.getName()
                    + "' has conflicting account references");
        }
        BankAccount account = accountService.getAccount(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Rule '" + rule.getName()
                        + "' refers to a missing account"));
        if (normalizeIban(account.getIban()).isEmpty()) {
            throw new IllegalArgumentException("Rule '" + rule.getName()
                    + "' refers to an account without an IBAN");
        }
        return new ExportedCondition(condition.getMatchField(), condition.getOperator(), null,
                condition.getAmountMin(), condition.getAmountMax(), account.getIban());
    }

    private ExportedAction exportAction(RuleAction action) {
        if (action.getActionType() == null) throw new IllegalArgumentException("Rule has an incomplete action");
        return switch (action.getActionType()) {
            case ASSIGN_CATEGORY -> new ExportedAction(action.getActionType(), categoryPath(action.getCategoryId()), null);
            case ADD_LABEL -> new ExportedAction(action.getActionType(), null,
                    labelService.getLabel(action.getLabelId())
                            .map(Label::getName)
                            .orElseThrow(() -> new IllegalArgumentException("Rule refers to a missing label")));
            case SET_NOTE -> throw new IllegalArgumentException("Rules that modify notes cannot be exported");
        };
    }

    private List<String> categoryPath(Long categoryId) {
        Map<Long, Category> categories = categoryService.getAllCategories().stream()
                .collect(Collectors.toMap(Category::getId, category -> category));
        List<String> path = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Category current = categories.get(categoryId);
        if (current == null) throw new IllegalArgumentException("Rule refers to a missing category");
        while (current != null) {
            if (!seen.add(current.getId())) throw new IllegalArgumentException("Category hierarchy contains a cycle");
            path.add(current.getName());
            Long parentId = current.getParentId();
            if (parentId == null) {
                current = null;
            } else {
                current = categories.get(parentId);
                if (current == null) {
                    throw new IllegalArgumentException("Category hierarchy contains a missing parent");
                }
            }
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private Rule importRule(ExportedRule exported) {
        if (exported == null || exported.name() == null || exported.name().isBlank()
                || exported.conditions() == null || exported.actions() == null) {
            throw new IllegalArgumentException("Rules export contains an incomplete rule");
        }
        List<RuleCondition> conditions = exported.conditions().stream().map(this::importCondition).toList();
        List<RuleAction> actions = exported.actions().stream().map(this::importAction).toList();
        ruleService.validateRuleForImport(conditions, actions);
        return Rule.builder().name(exported.name()).priority(exported.priority()).enabled(exported.enabled())
                .matchMode(exported.matchMode() == null ? RuleMatchMode.ALL : exported.matchMode())
                .conditions(conditions).actions(actions).build();
    }

    private RuleCondition importCondition(ExportedCondition exported) {
        if (exported == null || exported.matchField() == null || exported.operator() == null) {
            throw new IllegalArgumentException("Rules export contains an incomplete condition");
        }
        if (exported.matchField() == MatchField.ACCOUNT) {
            if (exported.accountIban() == null || exported.value() != null) {
                throw new IllegalArgumentException("Account rule condition has no portable account reference");
            }
            BankAccount account = findAccountByIban(exported.accountIban());
            return RuleCondition.builder().matchField(exported.matchField()).operator(exported.operator())
                    .value(account.getId().toString()).bankAccountId(account.getId())
                    .amountMin(exported.amountMin()).amountMax(exported.amountMax()).build();
        }
        if (exported.accountIban() != null) {
            throw new IllegalArgumentException("Non-account rule condition has an account reference");
        }
        return RuleCondition.builder().matchField(exported.matchField()).operator(exported.operator())
                .value(exported.value()).amountMin(exported.amountMin()).amountMax(exported.amountMax()).build();
    }

    private RuleAction importAction(ExportedAction exported) {
        if (exported == null || exported.actionType() == null) {
            throw new IllegalArgumentException("Rules export contains an incomplete action");
        }
        return switch (exported.actionType()) {
            case ASSIGN_CATEGORY -> {
                if (exported.categoryPath() == null || exported.labelName() != null) {
                    throw new IllegalArgumentException("Category rule action has no portable category path");
                }
                Category category = findCategoryByPath(exported.categoryPath());
                yield RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(category.getId()).build();
            }
            case ADD_LABEL -> {
                if (exported.labelName() == null || exported.categoryPath() != null) {
                    throw new IllegalArgumentException("Label rule action has no portable label name");
                }
                org.fintrax.model.Label label = findLabelByName(exported.labelName());
                yield RuleAction.builder().actionType(ActionType.ADD_LABEL).labelId(label.getId()).build();
            }
            case SET_NOTE -> throw new IllegalArgumentException("Rules that modify notes cannot be imported");
        };
    }

    private Category findCategoryByPath(List<String> path) {
        if (path.isEmpty() || path.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Rule has an invalid category path");
        }
        List<Category> categories = categoryService.getAllCategories();
        Long parentId = null;
        Category match = null;
        for (String segment : path) {
            Long currentParentId = parentId;
            List<Category> matches = categories.stream()
                    .filter(category -> Objects.equals(category.getParentId(), currentParentId))
                    .filter(category -> category.getName().equalsIgnoreCase(segment))
                    .toList();
            if (matches.size() != 1) {
                throw new IllegalArgumentException("Missing or ambiguous category path: " + String.join(" / ", path));
            }
            match = matches.getFirst();
            parentId = match.getId();
        }
        return match;
    }

    private org.fintrax.model.Label findLabelByName(String name) {
        List<org.fintrax.model.Label> matches = labelService.getAllLabels().stream()
                .filter(label -> label.getName().equalsIgnoreCase(name)).toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Missing or ambiguous label: " + name);
        }
        return matches.getFirst();
    }

    private BankAccount findAccountByIban(String iban) {
        String normalizedIban = normalizeIban(iban);
        List<BankAccount> matches = accountService.getAllAccounts().stream()
                .filter(account -> normalizedIban.equals(normalizeIban(account.getIban()))).toList();
        if (normalizedIban.isEmpty() || matches.size() != 1) {
            throw new IllegalArgumentException("Missing or ambiguous account for IBAN reference");
        }
        return matches.getFirst();
    }

    private void validateUniqueRuleNames(List<Rule> rules) {
        Set<String> names = new HashSet<>();
        for (Rule rule : rules) {
            String name = rule.getName();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Rule name is required for export");
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate rule name in export: " + name);
            }
        }
    }

    private String normalizeIban(String iban) {
        return iban == null ? "" : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private record ExportDocument(String format, int schemaVersion, String scope, ExportData data) {}
    private record ExportData(List<ExportedRule> rules) {}
    private record ExportedRule(String name, int priority, boolean enabled, RuleMatchMode matchMode,
                                List<ExportedCondition> conditions, List<ExportedAction> actions) {}
    private record ExportedCondition(MatchField matchField, Operator operator, String value,
                                     java.math.BigDecimal amountMin, java.math.BigDecimal amountMax,
                                     String accountIban) {}
    private record ExportedAction(ActionType actionType, List<String> categoryPath, String labelName) {}

    public record ImportResult(int added, int updated) {
        @Override
        public String toString() {
            return added + " added, " + updated + " updated";
        }
    }
}
