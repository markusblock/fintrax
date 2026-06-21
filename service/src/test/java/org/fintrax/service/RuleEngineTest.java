package org.fintrax.service;

import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {
    private Path tempDir;
    private StoreManager store;
    private ActivityLogger activityLogger;
    private RuleEngine ruleEngine;
    private TransactionService transactionService;
    private AccountService accountService;
    private CategoryService categoryService;
    private LabelService labelService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-test");
        store = new StoreManager(tempDir);
        activityLogger = new ActivityLogger(store);
        ruleEngine = new RuleEngine(store, activityLogger);
        transactionService = new TransactionService(store, activityLogger);
        accountService = new AccountService(store, activityLogger);
        categoryService = new CategoryService(store, activityLogger);
        labelService = new LabelService(store, activityLogger);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.shutdown();
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> {
            try { Files.delete(p); } catch (Exception e) {}
        });
    }

    @Test
    void testRuleMatchesPayeeContains() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);
        Category foodCategory = categoryService.createCategory("Food", null, null, null);

        Rule rule = Rule.builder()
                .id(1L).name("REWE Rule").priority(1).enabled(true)
                .conditions(List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME)
                        .operator(Operator.CONTAINS)
                        .value("REWE")
                        .build()))
                .actions(List.of(RuleAction.builder()
                        .actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(foodCategory.getId())
                        .build()))
                .build();

        store.getRoot().getRules().add(rule);

        Transaction tx = Transaction.builder()
                .accountId(account.getId())
                .originalPayee("REWE MARKT 1234 BERLIN")
                .amount(BigDecimal.valueOf(-25.50))
                .bookingDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .checksum("test1")
                .build();

        transactionService.createTransaction(tx);
        ruleEngine.applyRules(tx);

        assertEquals(foodCategory.getId(), tx.getCategoryId());
    }

    @Test
    void testRuleMatchesAmountRange() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);
        Label expensiveLabel = labelService.createLabel("Expensive", "#FF0000", null);

        Rule rule = Rule.builder()
                .id(1L).name("Big Purchase").priority(1).enabled(true)
                .conditions(List.of(RuleCondition.builder()
                        .matchField(MatchField.AMOUNT)
                        .operator(Operator.AMOUNT_RANGE)
                        .amountMin(BigDecimal.valueOf(-1000))
                        .amountMax(BigDecimal.valueOf(-100))
                        .build()))
                .actions(List.of(RuleAction.builder()
                        .actionType(ActionType.ADD_LABEL)
                        .labelId(expensiveLabel.getId())
                        .build()))
                .build();

        store.getRoot().getRules().add(rule);

        Transaction tx = Transaction.builder()
                .accountId(account.getId())
                .originalPayee("Electronics Store")
                .amount(BigDecimal.valueOf(-250.00))
                .bookingDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .checksum("test2")
                .build();

        transactionService.createTransaction(tx);
        ruleEngine.applyRules(tx);

        assertTrue(tx.getLabelIds().contains(expensiveLabel.getId()));
    }

    @Test
    void testFirstMatchWins() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);
        Category cat1 = categoryService.createCategory("Cat1", null, null, null);
        Category cat2 = categoryService.createCategory("Cat2", null, null, null);

        Rule rule1 = Rule.builder()
                .id(1L).name("Rule 1").priority(1).enabled(true)
                .conditions(List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME)
                        .operator(Operator.CONTAINS)
                        .value("REWE")
                        .build()))
                .actions(List.of(RuleAction.builder()
                        .actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(cat1.getId())
                        .build()))
                .build();

        Rule rule2 = Rule.builder()
                .id(2L).name("Rule 2").priority(2).enabled(true)
                .conditions(List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME)
                        .operator(Operator.CONTAINS)
                        .value("REWE")
                        .build()))
                .actions(List.of(RuleAction.builder()
                        .actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(cat2.getId())
                        .build()))
                .build();

        store.getRoot().getRules().addAll(List.of(rule1, rule2));

        Transaction tx = Transaction.builder()
                .accountId(account.getId())
                .originalPayee("REWE MARKT")
                .amount(BigDecimal.valueOf(-10.00))
                .bookingDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .checksum("test3")
                .build();

        transactionService.createTransaction(tx);
        ruleEngine.applyRules(tx);

        assertEquals(cat1.getId(), tx.getCategoryId());
    }

    @Test
    void testPreviewRule() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);

        transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("REWE A").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.now()).valueDate(LocalDate.now()).checksum("c1").build());
        transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("REWE B").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.now()).valueDate(LocalDate.now()).checksum("c2").build());
        transactionService.createTransaction(Transaction.builder()
                .accountId(account.getId()).originalPayee("Shell").amount(BigDecimal.ONE)
                .bookingDate(LocalDate.now()).valueDate(LocalDate.now()).checksum("c3").build());

        Rule rule = Rule.builder()
                .id(1L).name("REWE").priority(1).enabled(true)
                .conditions(List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME)
                        .operator(Operator.CONTAINS)
                        .value("REWE")
                        .build()))
                .actions(List.of())
                .build();

        assertEquals(2, ruleEngine.countMatchingTransactions(rule));
    }

    @Test
    void testDisabledRuleSkipped() {
        BankAccount account = accountService.createAccount(
                "DE89370400440532013000", null, "Bank", "User", AccountType.GIRO, null);
        Category cat = categoryService.createCategory("Cat", null, null, null);

        Rule rule = Rule.builder()
                .id(1L).name("Disabled").priority(1).enabled(false)
                .conditions(List.of(RuleCondition.builder()
                        .matchField(MatchField.PAYEE_NAME)
                        .operator(Operator.CONTAINS)
                        .value("REWE")
                        .build()))
                .actions(List.of(RuleAction.builder()
                        .actionType(ActionType.ASSIGN_CATEGORY)
                        .categoryId(cat.getId())
                        .build()))
                .build();

        store.getRoot().getRules().add(rule);

        Transaction tx = Transaction.builder()
                .accountId(account.getId())
                .originalPayee("REWE MARKT")
                .amount(BigDecimal.valueOf(-10.00))
                .bookingDate(LocalDate.now())
                .valueDate(LocalDate.now())
                .checksum("test4")
                .build();

        transactionService.createTransaction(tx);
        ruleEngine.applyRules(tx);

        assertNull(tx.getCategoryId());
    }
}
