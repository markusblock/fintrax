package org.fintrax.service.hibiscus;

import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
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
    void successfulImportPersistsTheCompleteRootForRestart() {
        importer.importFile(support.loadXmlTestData(IMPORT_TEST_DATA), true, true);

        store.shutdown();
        store = new StoreManager(tempDir);

        assertEquals(5, store.getRoot().getAccounts().size());
        assertEquals(5, store.getRoot().getTransactions().size());
        assertEquals(8, store.getRoot().getCategories().size());
        assertEquals(3, store.getRoot().getRules().size());
        assertEquals(5, store.getRoot().getActivityLogs().size());
    }

    @Test
    void reimportingWholeExportDoesNotDuplicateAccountsOrRules() {
        File xmlFile = support.loadXmlTestData(IMPORT_TEST_DATA);
        importer.importFile(xmlFile, true, true);

        BankAccount account = store.getRoot().getAccounts().stream()
                .filter(candidate -> "DE89370400440532013000".equals(candidate.getIban()))
                .findFirst().orElseThrow();
        account.setBankName("Edited bank name");
        account.setBalance(new BigDecimal("999.99"));
        Rule importedRule = store.getRoot().getRules().stream()
                .filter(rule -> rule.getName().equals("Imported: AGIP"))
                .findFirst().orElseThrow();
        importedRule.setName("Renamed imported rule");
        importedRule.setEnabled(false);

        HibiscusXmlImporter.ImportResult secondResult = importer.importFile(xmlFile, true, true);

        assertEquals(0, secondResult.getAccountsImported());
        assertEquals(0, secondResult.getTransactionsImported());
        assertEquals(0, secondResult.getCategoriesImported());
        assertEquals(0, secondResult.getRulesImported());
        assertEquals(5, store.getRoot().getAccounts().size());
        assertEquals(5, store.getRoot().getTransactions().size());
        assertEquals(8, store.getRoot().getCategories().size());
        assertEquals(3, store.getRoot().getRules().size());
        assertEquals("Edited bank name", account.getBankName());
        assertEquals(new BigDecimal("999.99"), account.getBalance());
        assertEquals("Renamed imported rule", importedRule.getName());
        assertFalse(importedRule.isEnabled());
    }

    @Test
    void accountMatchingNormalizesIbanWhitespaceAndCase() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath());
        File firstImport = writeImportFile("first-import.xml", xml);
        File secondImport = writeImportFile("second-import.xml", xml.replaceFirst(
                "DE89370400440532013000", " de89 3704 0044 0532 0130 00 "));

        importer.importFile(firstImport, false, false);
        HibiscusXmlImporter.ImportResult secondResult = importer.importFile(secondImport, false, false);

        assertEquals(0, secondResult.getAccountsImported());
        assertEquals(5, store.getRoot().getAccounts().size());
    }

    @Test
    void accountsWithoutIbanAreImportedAsNewRecords() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath());
        String withoutFirstIban = xml.replaceFirst(
                "<iban type=\"java.lang.String\">DE89370400440532013000</iban>",
                "<iban type=\"java.lang.String\"></iban>");
        File importFile = writeImportFile("without-iban.xml", withoutFirstIban);

        importer.importFile(importFile, false, false);
        HibiscusXmlImporter.ImportResult secondResult = importer.importFile(importFile, false, false);

        assertEquals(1, secondResult.getAccountsImported());
        assertEquals(6, store.getRoot().getAccounts().size());
    }

    @Test
    void changedImportedRuleBehaviorCreatesNewRule() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath());
        File firstImport = writeImportFile("first-import.xml", xml);
        File changedRuleImport = writeImportFile("changed-rule-import.xml", xml.replaceFirst(
                "<pattern type=\"java.lang.String\">AGIP</pattern>",
                "<pattern type=\"java.lang.String\">AGIP UPDATED</pattern>"));

        importer.importFile(firstImport, true, true);
        HibiscusXmlImporter.ImportResult secondResult = importer.importFile(changedRuleImport, true, true);

        assertEquals(1, secondResult.getRulesImported());
        assertEquals(4, store.getRoot().getRules().size());
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
    void importedCategoryRulesDoNotCategorizeImportedTransactions() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath())
                .replaceFirst("VISA Test GmbH", "AGIP");
        File xmlFile = writeImportFile("transaction-matching-imported-rule.xml", xml);

        importer.importFile(xmlFile, true, true);

        Transaction importedTransaction = store.getRoot().getTransactions().stream()
                .filter(transaction -> "AGIP".equals(transaction.getOriginalPayee()))
                .findFirst().orElseThrow();
        assertNull(importedTransaction.getCategoryId());
        assertTrue(store.getRoot().getRules().stream().anyMatch(rule -> rule.getName().equals("Imported: AGIP")));
    }

    @Test
    void importedTransactionKeepsItsMappedHibiscusCategoryAssignment() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath());
        String transactionStart = "<object type=\"de.willuhn.jameica.hbci.server.UmsatzImpl\" id=\"1\">";
        int start = xml.indexOf(transactionStart);
        int end = xml.indexOf("</object>", start);
        String categoryAssignment = "\n    <umsatztyp_id type=\"java.lang.Integer\">2</umsatztyp_id>";
        String withCategoryAssignment = xml.substring(0, end) + categoryAssignment + xml.substring(end);
        File xmlFile = writeImportFile("assigned-transaction.xml", withCategoryAssignment);

        importer.importFile(xmlFile, true, true);

        Long importedCategoryId = store.getRoot().getCategories().stream()
                .filter(category -> "Auto".equals(category.getName()))
                .map(Category::getId).findFirst().orElseThrow();
        Transaction importedTransaction = store.getRoot().getTransactions().stream()
                .filter(transaction -> "VISA Test GmbH".equals(transaction.getOriginalPayee()))
                .findFirst().orElseThrow();
        assertEquals(importedCategoryId, importedTransaction.getCategoryId());
    }

    @Test
    void failedImportRollsBackAllPartialDataInMemoryAndAfterRestart() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <objects>
                  <object type="de.willuhn.jameica.hbci.server.KontoImpl" id="1">
                    <bezeichnung type="java.lang.String">Partial account</bezeichnung>
                    <saldo type="java.lang.Double">10.0</saldo>
                    <iban type="java.lang.String"></iban>
                  </object>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="2">
                    <name type="java.lang.String">Imported category</name>
                  </object>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="3">
                    <parent_id type="java.lang.Integer">99</parent_id>
                    <name type="java.lang.String">Orphan category</name>
                  </object>
                </objects>
                """;
        File xmlFile = writeImportFile("failed-import.xml", xml);

        assertThrows(IllegalArgumentException.class, () -> importer.importFile(xmlFile, true, true));

        assertTrue(store.getRoot().getAccounts().isEmpty());
        assertTrue(store.getRoot().getCategories().isEmpty());
        assertTrue(store.getRoot().getTransactions().isEmpty());
        assertTrue(store.getRoot().getRules().isEmpty());

        store.shutdown();
        store = new StoreManager(tempDir);
        assertTrue(store.getRoot().getAccounts().isEmpty());
        assertTrue(store.getRoot().getCategories().isEmpty());
        assertTrue(store.getRoot().getTransactions().isEmpty());
        assertTrue(store.getRoot().getRules().isEmpty());
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
    void sameChecksumIdentifiesTransactionDespiteChangedDescription() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath());
        File firstImport = writeImportFile("first-import.xml", xml);
        File secondImport = writeImportFile("second-import.xml", xml.replaceFirst(
                "<empfaenger_name type=\"java.lang.String\">VISA Test GmbH</empfaenger_name>",
                "<empfaenger_name type=\"java.lang.String\">Changed payee</empfaenger_name>"));

        importer.importFile(firstImport, false, false);
        HibiscusXmlImporter.ImportResult secondResult = importer.importFile(secondImport, false, false);

        assertEquals(0, secondResult.getTransactionsImported());
        assertEquals(5, store.getRoot().getTransactions().size());
    }

    @Test
    void sameTransactionFieldsWithDifferentChecksumsAreImportedSeparately() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath());
        File firstImport = writeImportFile("first-import.xml", xml);
        File secondImport = writeImportFile("second-import.xml", xml.replaceFirst(
                "<checksum type=\"java.math.BigDecimal\">1647417865</checksum>",
                "<checksum type=\"java.math.BigDecimal\">987654321</checksum>"));

        importer.importFile(firstImport, false, false);
        HibiscusXmlImporter.ImportResult secondResult = importer.importFile(secondImport, false, false);

        assertEquals(1, secondResult.getTransactionsImported());
        assertEquals(6, store.getRoot().getTransactions().size());
    }

    @Test
    void transactionsWithoutChecksumsUseNormalizedFieldsToDetectDuplicates() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath());
        String withoutFirstChecksum = xml.replaceFirst(
                "<checksum type=\"java.math.BigDecimal\">1647417865</checksum>", "");
        String changedPayee = withoutFirstChecksum.replaceFirst(
                "<empfaenger_name type=\"java.lang.String\">VISA Test GmbH</empfaenger_name>",
                "<empfaenger_name type=\"java.lang.String\">  visa   test gmbh  </empfaenger_name>");
        String changedPurpose = changedPayee.replaceFirst(
                "<zweck type=\"java.lang.String\">SVWZ\\+NR XXXX 1024 Pforzheim</zweck>",
                "<zweck type=\"java.lang.String\"> svwz+nr   xxxx 1024 pforzheim </zweck>");
        File firstImport = writeImportFile("first-import.xml", xml);
        File secondImport = writeImportFile("second-import.xml", changedPurpose);

        importer.importFile(firstImport, false, false);
        HibiscusXmlImporter.ImportResult secondResult = importer.importFile(secondImport, false, false);

        assertEquals(0, secondResult.getTransactionsImported());
        assertEquals(5, store.getRoot().getTransactions().size());
    }

    @Test
    void bookingDateDistinguishesTransactionsWithoutChecksums() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath());
        String withoutFirstChecksum = xml.replaceFirst(
                "<checksum type=\"java.math.BigDecimal\">1647417865</checksum>", "");
        String changedDate = withoutFirstChecksum.replaceFirst(
                "<datum type=\"java.sql.Date\">15.03.2024 00:00:00</datum>",
                "<datum type=\"java.sql.Date\">16.03.2024 00:00:00</datum>");
        File firstImport = writeImportFile("first-import.xml", withoutFirstChecksum);
        File secondImport = writeImportFile("second-import.xml", changedDate);

        importer.importFile(firstImport, false, false);
        HibiscusXmlImporter.ImportResult secondResult = importer.importFile(secondImport, false, false);

        assertEquals(1, secondResult.getTransactionsImported());
        assertEquals(6, store.getRoot().getTransactions().size());
    }

    @Test
    void sameChecksumOnDifferentAccountsDoesNotMarkTransactionsAsDuplicates() throws IOException {
        String xml = Files.readString(support.loadXmlTestData(IMPORT_TEST_DATA).toPath());
        String transactionOnAnotherAccount = xml.replaceFirst(
                "<konto_id type=\"java.lang.Integer\">1</konto_id>",
                "<konto_id type=\"java.lang.Integer\">2</konto_id>");
        File firstImport = writeImportFile("first-import.xml", xml);
        File secondImport = writeImportFile("second-import.xml", transactionOnAnotherAccount);

        importer.importFile(firstImport, false, false);
        HibiscusXmlImporter.ImportResult secondResult = importer.importFile(secondImport, false, false);

        assertEquals(1, secondResult.getTransactionsImported());
        assertEquals(6, store.getRoot().getTransactions().size());
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

    private File writeImportFile(String name, String xml) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, xml);
        return path.toFile();
    }
}
