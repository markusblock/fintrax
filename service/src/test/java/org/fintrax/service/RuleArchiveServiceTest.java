package org.fintrax.service;

import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class RuleArchiveServiceTest {
    private final List<Fixture> fixtures = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Fixture fixture : fixtures) fixture.close();
    }

    @Test
    void exportedArchiveContainsPortableRuleReferencesAndNoOtherData() throws Exception {
        Fixture source = fixture();
        source.createRule("Travel", 1, true);
        Path archive = Files.createTempFile(source.directory, "rules-export", ".zip");

        source.archiveService.exportRules(archive);

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            assertNotNull(entry);
            assertEquals("rules.json", entry.getName());
            String json = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"format\":\"fintrax-export\""));
            assertTrue(json.contains("\"schemaVersion\":1"));
            assertTrue(json.contains("\"scope\":\"PARTIAL\""));
            assertTrue(json.contains("Travel"));
            assertTrue(json.contains("DE89370400440532013000"));
            assertTrue(json.contains("Meals"));
            assertTrue(json.contains("Urgent"));
            assertFalse(json.contains("categoryId"));
            assertFalse(json.contains("labelId"));
            assertFalse(json.contains("accountHolder"));
            assertNull(zip.getNextEntry());
        }
    }

    @Test
    void importUpdatesSameNameRuleAddsNewRuleAndLeavesOmittedRulesUnchanged() throws Exception {
        Fixture source = fixture();
        source.createRule("Travel", 1, true);
        source.createRule("Food", 2, false);
        Path archive = Files.createTempFile(source.directory, "rules-export", ".zip");
        source.archiveService.exportRules(archive);

        Fixture target = fixtureWithShiftedIds();
        Rule existing = target.createRule("travel", 9, false);
        Rule omitted = target.createRule("Keep me", 10, true);
        Long existingId = existing.getId();
        Long omittedId = omitted.getId();

        RuleArchiveService.ImportResult result = target.archiveService.importRules(archive);

        assertEquals(1, result.added());
        assertEquals(1, result.updated());
        Rule updated = target.ruleService.getAllRules().stream()
                .filter(rule -> rule.getId().equals(existingId)).findFirst().orElseThrow();
        assertEquals("Travel", updated.getName());
        assertEquals(1, updated.getPriority());
        assertTrue(updated.isEnabled());
        assertEquals(target.account.getId().toString(), updated.getConditions().getFirst().getValue());
        assertEquals(target.meals.getId(), updated.getActions().getFirst().getCategoryId());
        assertEquals(target.label.getId(), updated.getActions().get(1).getLabelId());
        assertTrue(target.ruleService.getAllRules().stream().anyMatch(rule -> rule.getName().equals("Food")));
        Rule stillPresent = target.ruleService.getRule(omittedId).orElseThrow();
        assertEquals("Keep me", stillPresent.getName());
        assertTrue(stillPresent.isEnabled());

        RuleArchiveService.ImportResult repeated = target.archiveService.importRules(archive);
        assertEquals(0, repeated.added());
        assertEquals(2, repeated.updated());
        assertEquals(3, target.ruleService.getAllRules().size());
    }

    @Test
    void importRejectsMissingReferencesBeforeChangingAnyRules() throws Exception {
        Fixture source = fixture();
        source.createRule("Travel", 1, true);
        Path archive = Files.createTempFile(source.directory, "rules-export", ".zip");
        source.archiveService.exportRules(archive);

        Fixture target = fixtureWithAccountAndLabelOnly();
        Rule existing = target.createRule("Keep me", 1, true);
        Long existingId = existing.getId();

        assertThrows(IllegalArgumentException.class, () -> target.archiveService.importRules(archive));

        assertEquals(1, target.ruleService.getAllRules().size());
        assertEquals("Keep me", target.ruleService.getRule(existingId).orElseThrow().getName());
    }

    @Test
    void importAcceptsStandaloneJsonPayload() throws Exception {
        Fixture source = fixture();
        source.createRule("Travel", 1, true);
        Path zipArchive = Files.createTempFile(source.directory, "rules-export", ".zip");
        Path jsonFile = source.directory.resolve("rules.json");
        source.archiveService.exportRules(zipArchive);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipArchive))) {
            assertNotNull(zip.getNextEntry());
            Files.write(jsonFile, zip.readAllBytes());
        }

        Fixture target = fixture();

        RuleArchiveService.ImportResult result = target.archiveService.importRules(jsonFile);

        assertEquals(1, result.added());
        assertEquals(0, result.updated());
        assertEquals("Travel", target.ruleService.getAllRules().getFirst().getName());
    }

    @Test
    void exportRejectsAccountConditionsThatCannotBeMappedPortably() throws Exception {
        Fixture source = fixture();
        source.ruleService.createRule("Contains account ID",
                List.of(RuleCondition.builder().matchField(MatchField.ACCOUNT).operator(Operator.CONTAINS)
                        .value(source.account.getId().toString()).build()),
                List.of(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(source.meals.getId()).build()));
        Path archive = Files.createTempFile(source.directory, "rules-export", ".zip");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> source.archiveService.exportRules(archive));

        assertTrue(error.getMessage().contains("cannot be exported portably"));
        assertEquals(0, Files.size(archive));
    }

    private Fixture fixture() throws Exception {
        Fixture fixture = new Fixture(false, false);
        fixtures.add(fixture);
        return fixture;
    }

    private Fixture fixtureWithShiftedIds() throws Exception {
        Fixture fixture = new Fixture(true, false);
        fixtures.add(fixture);
        return fixture;
    }

    private Fixture fixtureWithAccountAndLabelOnly() throws Exception {
        Fixture fixture = new Fixture(false, true);
        fixtures.add(fixture);
        return fixture;
    }

    private static class Fixture implements AutoCloseable {
        private final Path directory;
        private final StoreManager store;
        private final RuleService ruleService;
        private final AccountService accountService;
        private final CategoryService categoryService;
        private final LabelService labelService;
        private final RuleArchiveService archiveService;
        private final BankAccount account;
        private final Category meals;
        private final org.fintrax.model.Label label;

        private Fixture(boolean shiftIds, boolean omitCategory) throws Exception {
            directory = Files.createTempDirectory("fintrax-rule-archive-test");
            store = new StoreManager(directory);
            ActivityLogger activityLogger = new ActivityLogger(store);
            RuleEngine ruleEngine = new RuleEngine(store, activityLogger);
            ruleService = new RuleService(store, activityLogger, ruleEngine);
            accountService = new AccountService(store, activityLogger);
            categoryService = new CategoryService(store, activityLogger);
            labelService = new LabelService(store, activityLogger);
            archiveService = new RuleArchiveService(ruleService, accountService, categoryService, labelService);
            if (shiftIds) {
                accountService.createAccount("DE00000000000000000001", null, "Other", "Other", AccountType.GIRO, null);
                categoryService.createCategory("Other", null, null);
                labelService.createLabel("Other", null, null);
            }
            account = accountService.createAccount("DE89370400440532013000", null, "Bank", "Owner",
                    AccountType.GIRO, null);
            if (!omitCategory) {
                Category expenses = categoryService.createCategory("Expenses", null, null);
                meals = categoryService.createCategory("Meals", expenses.getId(), null);
            } else {
                meals = null;
            }
            label = labelService.createLabel("Urgent", "#ff0000", null);
        }

        private Rule createRule(String name, int priority, boolean enabled) {
            Rule rule = ruleService.createRule(name,
                    List.of(RuleCondition.builder().matchField(MatchField.ACCOUNT).operator(Operator.EQUALS)
                            .value(account.getId().toString()).build()),
                    List.of(RuleAction.builder().actionType(ActionType.ASSIGN_CATEGORY)
                                    .categoryId(meals == null ? null : meals.getId()).build(),
                            RuleAction.builder().actionType(ActionType.ADD_LABEL).labelId(label.getId()).build()));
            rule.setPriority(priority);
            rule.setEnabled(enabled);
            store.store(rule);
            return rule;
        }

        @Override
        public void close() throws Exception {
            store.shutdown();
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
