package org.fintrax.service.hibiscus;

import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HibiscusXmlImporterTest {
    private static final String IMPORT_TEST_DATA = "/org/fintrax/service/hibiscus/hibiscus-import-test-data.xml";

    private final HibiscusXmlImportTestSupport support = new HibiscusXmlImportTestSupport();
    private Path tempDir;
    private StoreManager store;
    private HibiscusXmlImporter importer;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-test");
        store = new StoreManager(tempDir);
        importer = new HibiscusXmlImporter(store);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.shutdown();
        support.deleteTempFiles(tempDir);
    }

    @Test
    void testImportAll() {
        File xmlFile = support.loadXmlTestData(IMPORT_TEST_DATA);

        HibiscusXmlImporter.ImportResult result = importer.importFile(xmlFile, true, true);

        assertEquals(5, result.getAccountsImported());
        assertEquals(5, result.getTransactionsImported());
        assertEquals(8, result.getCategoriesImported());
        assertEquals(3, result.getRulesImported());
        assertEquals(5, result.getActivityLogsImported());

        assertEquals(5, store.getRoot().getAccounts().size());
        assertEquals(5, store.getRoot().getTransactions().size());
        assertEquals(8, store.getRoot().getCategories().size());
        assertEquals(3, store.getRoot().getRules().size());
        assertEquals(5, store.getRoot().getActivityLogs().size());
    }

    @Test
    void testImportWithoutCategoriesAndRules() {
        File xmlFile = support.loadXmlTestData(IMPORT_TEST_DATA);

        HibiscusXmlImporter.ImportResult result = importer.importFile(xmlFile, false, false);

        assertEquals(5, result.getAccountsImported());
        assertEquals(5, result.getTransactionsImported());
        assertEquals(0, result.getCategoriesImported());
        assertEquals(0, result.getRulesImported());
        assertEquals(5, result.getActivityLogsImported());
    }

    @Test
    void testImportAccountMapping() {
        File xmlFile = support.loadXmlTestData(IMPORT_TEST_DATA);
        importer.importFile(xmlFile, true, true);

        BankAccount firstAccount = store.getRoot().getAccounts().stream()
                .filter(a -> "DE89370400440532013000".equals(a.getIban()))
                .findFirst().orElse(null);

        assertNotNull(firstAccount);
        assertEquals("Testkonto SEPA", firstAccount.getBankName());
        assertEquals("Max Mustermann", firstAccount.getAccountHolder());
        assertEquals(new BigDecimal("4.67"), firstAccount.getBalance());
        assertEquals("EUR", firstAccount.getCurrency());
        assertEquals("COBADEFFXXX", firstAccount.getBic());
        assertEquals(AccountType.GIRO, firstAccount.getAccountType());
    }

    @Test
    void testImportTransactionMapping() {
        File xmlFile = support.loadXmlTestData(IMPORT_TEST_DATA);
        importer.importFile(xmlFile, true, true);

        Transaction firstTx = store.getRoot().getTransactions().stream()
                .filter(t -> "VISA Test GmbH".equals(t.getOriginalPayee()))
                .findFirst().orElse(null);

        assertNotNull(firstTx);
        assertEquals(new BigDecimal("-14.54"), firstTx.getAmount());
        assertNotNull(firstTx.getAccountId());
        assertTrue(firstTx.getPurpose().contains("SVWZ+NR XXXX 1024 Pforzheim"));
        assertEquals("Lastschrifteinzug", firstTx.getTransactionType());
    }

    @Test
    void testImportRulesFromPatterns() {
        File xmlFile = support.loadXmlTestData(IMPORT_TEST_DATA);
        importer.importFile(xmlFile, true, true);

        assertEquals(3, store.getRoot().getRules().size());

        Rule agipRule = store.getRoot().getRules().stream()
                .filter(r -> r.getName().contains("AGIP"))
                .findFirst().orElse(null);

        assertNotNull(agipRule);
        assertEquals(1, agipRule.getConditions().size());
        assertEquals(MatchField.PAYEE_NAME, agipRule.getConditions().get(0).getMatchField());
        assertEquals(Operator.CONTAINS, agipRule.getConditions().get(0).getOperator());
        assertEquals("AGIP", agipRule.getConditions().get(0).getValue());
        assertEquals(1, agipRule.getActions().size());
        assertEquals(ActionType.ASSIGN_CATEGORY, agipRule.getActions().get(0).getActionType());

        Rule supermarktRule = store.getRoot().getRules().stream()
                .filter(r -> r.getName().contains("Supermarkt"))
                .findFirst().orElse(null);

        assertNotNull(supermarktRule);
        assertEquals(Operator.REGEX, supermarktRule.getConditions().get(0).getOperator());
        assertEquals("REWE|EDEKA|ALDI", supermarktRule.getConditions().get(0).getValue());
    }

    @Test
    void testImportSavingsAccountType() {
        File xmlFile = support.loadXmlTestData(IMPORT_TEST_DATA);
        importer.importFile(xmlFile, true, true);

        BankAccount sparkonto = store.getRoot().getAccounts().stream()
                .filter(a -> "Sparkasse Sparkonto".equals(a.getBankName()))
                .findFirst().orElse(null);

        assertNotNull(sparkonto);
        assertEquals(AccountType.SAVINGS, sparkonto.getAccountType());
    }
}