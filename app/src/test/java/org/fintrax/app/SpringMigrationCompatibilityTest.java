package org.fintrax.app;

import org.fintrax.model.*;
import org.fintrax.service.*;
import org.fintrax.service.hibiscus.HibiscusXmlImporter;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.fintrax.store.DataRoot;
import org.fintrax.store.ResetGroup;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringMigrationCompatibilityTest {
    @TempDir
    Path storagePath;

    @Test
    void opensPreMigrationDataAndPreservesItAcrossSpringRestart() throws IOException {
        createPreMigrationFixture();

        try (ConfigurableApplicationContext context = startContext()) {
            DataRoot root = context.getBean(StoreManager.class).getRoot();

            assertAll(
                    () -> assertEquals("Legacy Bank", root.getAccounts().getFirst().getBankName()),
                    () -> assertEquals("Legacy purchase", root.getTransactions().getFirst().getOriginalPayee()),
                    () -> assertEquals("Food", root.getCategories().getFirst().getName()),
                    () -> assertEquals("Important", root.getLabels().getFirst().getName()),
                    () -> assertEquals("Legacy rule", root.getRules().getFirst().getName()),
                    () -> assertEquals(SyncStatus.SUCCESS, root.getSyncLogs().getFirst().getStatus()),
                    () -> assertEquals("Imported before Spring", root.getActivityLogs().getFirst().getDescription()),
                    () -> assertEquals("dark", root.getSettings().get(SettingsService.THEME_KEY).getValue())
            );
        }

        try (ConfigurableApplicationContext context = startContext()) {
            StoreManager store = context.getBean(StoreManager.class);
            AccountService accounts = context.getBean(AccountService.class);
            TransactionService transactions = context.getBean(TransactionService.class);
            SettingsService settings = context.getBean(SettingsService.class);
            ActivityLogger activityLogger = context.getBean(ActivityLogger.class);
            SyncService sync = context.getBean(SyncService.class);
            HibiscusXmlImporter importer = context.getBean(HibiscusXmlImporter.class);

            BankAccount created = accounts.createAccount(
                    "DE00000000000000000001", "TESTDEFF", "Spring Bank", "Test User",
                    AccountType.GIRO, "created after migration");
            Transaction createdTransaction = transactions.createTransaction(Transaction.builder()
                    .accountId(created.getId())
                    .originalPayee("Spring purchase")
                    .amount(BigDecimal.TEN)
                    .bookingDate(LocalDate.now())
                    .valueDate(LocalDate.now())
                    .checksum("spring-checksum")
                    .build());
            settings.saveLanguage("de");
            activityLogger.log(ActivityAction.UPDATE, EntityType.SETTINGS, null, "Changed language");
            File importFile = writeImportFixture();
            importer.importFile(importFile, true, true);

            assertAll(
                    () -> assertEquals("de", settings.getLanguage()),
                    () -> assertNotNull(store.getAccount(created.getId())),
                    () -> assertNotNull(store.getTransaction(createdTransaction.getId())),
                    () -> assertTrue(store.getRoot().getActivityLogs().stream()
                            .anyMatch(log -> "Changed language".equals(log.getDescription()))),
                    () -> assertEquals(1, sync.getSyncLogs(1L).size()),
                    () -> assertTrue(store.getRoot().getCategories().stream()
                            .anyMatch(category -> "Imported category".equals(category.getName()))),
                    () -> assertTrue(store.getRoot().getRules().stream()
                            .anyMatch(rule -> rule.getName().startsWith("Imported: ")))
            );
        }

        try (ConfigurableApplicationContext context = startContext()) {
            StoreManager store = context.getBean(StoreManager.class);
            SettingsService settings = context.getBean(SettingsService.class);

            assertAll(
                    () -> assertEquals(3, store.getRoot().getAccounts().size()),
                    () -> assertTrue(store.getRoot().getTransactions().stream()
                            .anyMatch(transaction -> "Legacy purchase".equals(transaction.getOriginalPayee()))),
                    () -> assertTrue(store.getRoot().getTransactions().stream()
                            .anyMatch(transaction -> "Spring purchase".equals(transaction.getOriginalPayee()))),
                    () -> assertEquals("Legacy Bank", store.getAccount(1L).getBankName()),
                    () -> assertEquals("Important", store.getLabel(3L).getName()),
                    () -> assertEquals("Legacy rule", store.getRule(5L).getName()),
                    () -> assertEquals("de", settings.getLanguage()),
                    () -> assertEquals("dark", settings.getTheme()),
                    () -> assertEquals(SyncStatus.SUCCESS, store.getRoot().getSyncLogs().getFirst().getStatus()),
                    () -> assertTrue(store.getRoot().getActivityLogs().stream()
                            .anyMatch(log -> "Imported before Spring".equals(log.getDescription())))
            );
        }
    }

    @Test
    void resetKeepsLegacyDataOutsideSelectedGroup() {
        createPreMigrationFixture();

        try (ConfigurableApplicationContext context = startContext()) {
            StoreManager store = context.getBean(StoreManager.class);
            context.getBean(ResetService.class).reset(Set.of(ResetGroup.APPLICATION_SETTINGS));

            assertAll(
                    () -> assertTrue(store.getRoot().getSettings().isEmpty()),
                    () -> assertFalse(store.getRoot().getAccounts().isEmpty()),
                    () -> assertFalse(store.getRoot().getTransactions().isEmpty()),
                    () -> assertFalse(store.getRoot().getCategories().isEmpty()),
                    () -> assertFalse(store.getRoot().getLabels().isEmpty()),
                    () -> assertFalse(store.getRoot().getRules().isEmpty()),
                    () -> assertFalse(store.getRoot().getSyncLogs().isEmpty()),
                    () -> assertFalse(store.getRoot().getActivityLogs().isEmpty())
            );
        }
    }

    private ConfigurableApplicationContext startContext() {
        return FintraxSpringBootstrap.start(
                "--spring.main.banner-mode=off",
                "--fintrax.storage.path=" + storagePath);
    }

    private void createPreMigrationFixture() {
        EmbeddedStorageManager legacyStore = EmbeddedStorage.start(storagePath);
        try {
            BankAccount account = BankAccount.builder()
                    .id(1L)
                    .iban("DE12345678901234567890")
                    .bankName("Legacy Bank")
                    .accountHolder("Legacy User")
                    .accountType(AccountType.GIRO)
                    .build();
            Category category = Category.builder().id(2L).name("Food").build();
            Label label = Label.builder().id(3L).name("Important").build();
            Transaction transaction = Transaction.builder()
                    .id(4L)
                    .accountId(account.getId())
                    .originalPayee("Legacy purchase")
                    .amount(BigDecimal.valueOf(-12.50))
                    .bookingDate(LocalDate.of(2025, 1, 15))
                    .valueDate(LocalDate.of(2025, 1, 15))
                    .categoryId(category.getId())
                    .labelIds(new HashSet<>(Set.of(label.getId())))
                    .checksum("legacy-checksum")
                    .build();
            Rule rule = Rule.builder().id(5L).name("Legacy rule").priority(1).build();
            SyncLog syncLog = SyncLog.builder()
                    .id(6L)
                    .bankAccountId(account.getId())
                    .status(SyncStatus.SUCCESS)
                    .newCount(1)
                    .build();
            ActivityLog activityLog = ActivityLog.builder()
                    .id(7L)
                    .action(ActivityAction.CREATE)
                    .entityType(EntityType.TRANSACTION)
                    .description("Imported before Spring")
                    .build();
            AppSetting theme = AppSetting.builder()
                    .key(SettingsService.THEME_KEY)
                    .value("dark")
                    .build();

            DataRoot root = legacyStore.ensureRoot(DataRoot::new);
            root.setAccounts(new ArrayList<>(List.of(account)));
            root.setTransactions(new ArrayList<>(List.of(transaction)));
            root.setCategories(new ArrayList<>(List.of(category)));
            root.setLabels(new ArrayList<>(List.of(label)));
            root.setRules(new ArrayList<>(List.of(rule)));
            root.setSyncLogs(new ArrayList<>(List.of(syncLog)));
            root.setActivityLogs(new ArrayList<>(List.of(activityLog)));
            root.getSettings().put(theme.getKey(), theme);
            legacyStore.store(root);
            legacyStore.store(root.getAccounts());
            legacyStore.store(root.getTransactions());
            legacyStore.store(root.getCategories());
            legacyStore.store(root.getLabels());
            legacyStore.store(root.getRules());
            legacyStore.store(root.getSyncLogs());
            legacyStore.store(root.getActivityLogs());
            legacyStore.store(root.getSettings());
        } finally {
            legacyStore.shutdown();
        }
    }

    private File writeImportFixture() throws IOException {
        Path importPath = storagePath.resolve("import.xml");
        java.nio.file.Files.writeString(importPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <objects>
                  <object type="de.willuhn.jameica.hbci.server.KontoImpl" id="10">
                    <bezeichnung type="java.lang.String">Imported account</bezeichnung>
                    <iban type="java.lang.String">DE11111111111111111111</iban>
                    <name type="java.lang.String">Imported User</name>
                    <waehrung type="java.lang.String">EUR</waehrung>
                  </object>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="20">
                    <name type="java.lang.String">Imported category</name>
                    <pattern type="java.lang.String">Imported</pattern>
                    <isregex type="java.lang.Integer">0</isregex>
                  </object>
                </objects>
                """);
        return importPath.toFile();
    }
}
