package org.fintrax.service.hibiscus;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.DataRoot;
import org.fintrax.store.StoreManager;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class HibiscusXmlImporter {
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy[ HH:mm:ss]");

    private final StoreManager storeManager;

    public HibiscusXmlImporter(StoreManager storeManager) {
        this.storeManager = storeManager;
    }

    public ImportResult importFile(File xmlFile, boolean importCategories, boolean importRules) {
        log.info("Importing Hibiscus XML from {}", xmlFile.getAbsolutePath());

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList objects = doc.getElementsByTagName("object");
            log.info("Found {} objects in XML", objects.getLength());

            Map<Integer, Long> accountIdMap = new HashMap<>();
            Map<Integer, Long> hibiscusToFintraxCategoryIdMap = new HashMap<>();
            List<CategoryImport> categoryImports = new ArrayList<>();
            List<TransactionImport> transactionImports = new ArrayList<>();
            List<ActivityLogImport> activityImports = new ArrayList<>();

            for (int i = 0; i < objects.getLength(); i++) {
                Element elem = (Element) objects.item(i);
                String type = elem.getAttribute("type");
                int hibiscusId = Integer.parseInt(elem.getAttribute("id"));

                if (type.contains("KontoImpl")) {
                    parseKonto(elem, hibiscusId, accountIdMap);
                } else if (type.contains("UmsatzImpl")) {
                    transactionImports.add(parseUmsatz(elem, hibiscusId));
                } else if (type.contains("UmsatzTypImpl")) {
                    categoryImports.add(parseUmsatzTyp(elem, hibiscusId));
                } else if (type.contains("ProtokollImpl")) {
                    activityImports.add(parseProtokoll(elem, hibiscusId));
                }
            }

            DataRoot root = storeManager.getRoot();
            int transactionsCreated = 0;
            int categoriesCreated = 0;
            int rulesCreated = 0;
            int activitiesCreated = 0;

            if (importCategories) {
                rejectDuplicateSourceCategories(categoryImports);

                // Process categories in topological order (parents before children),
                // retrying until no more progress is possible. XML order is irrelevant.
                int processedCount;
                do {
                    processedCount = 0;
                    for (var catImport : categoryImports) {
                        // Skip if already processed
                        if (hibiscusToFintraxCategoryIdMap.containsKey(catImport.hibiscusId)) {
                            continue;
                        }

                        // Resolve parent fintrax ID from the hibiscus parent ID
                        Long parentFintraxId = resolveParentFintraxId(
                                hibiscusToFintraxCategoryIdMap, catImport.parentHibiscusId);

                        // Parent not resolved yet, will try again in next iteration
                        if (parentFintraxId == null && catImport.parentHibiscusId != null) {
                            continue;
                        }

                        // Check if category already exists in fintrax
                        if (shouldSkipCategoryImport(catImport, parentFintraxId, root)) {
                            // Map hibiscus ID to existing fintrax category ID
                            findExistingFintraxCategory(root, catImport.name, parentFintraxId)
                                    .ifPresent(cat -> {
                                        hibiscusToFintraxCategoryIdMap.put(catImport.hibiscusId, cat.getId());
                                        log.debug("Skipping duplicate category: {} (parent: {})", catImport.name,
                                                parentFintraxId != null ? "ID:" + parentFintraxId : "none");
                                    });
                        } else {
                            long newId = nextCategoryId(root);
                            Category cat = Category.builder()
                                    .id(newId)
                                    .name(catImport.name)
                                    .parentId(parentFintraxId)
                                    .color(catImport.color)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            root.getCategories().add(cat);
                            hibiscusToFintraxCategoryIdMap.put(catImport.hibiscusId, newId);
                            categoriesCreated++;
                            log.debug("Created new category: {} (parent: {})", catImport.name,
                                    parentFintraxId != null ? "ID:" + parentFintraxId : "none");
                        }
                        processedCount++;
                    }
                } while (processedCount > 0 && hibiscusToFintraxCategoryIdMap.size() < categoryImports.size());

                // Any remaining category has an unresolvable parent: invalid input, fail clearly
                for (var catImport : categoryImports) {
                    if (hibiscusToFintraxCategoryIdMap.containsKey(catImport.hibiscusId)) {
                        continue;
                    }
                    throw new IllegalArgumentException(
                            "Category '" + catImport.name + "' has unresolved parent (hibiscus ID: "
                                    + catImport.parentHibiscusId + "); expected a full category export");
                }
            } else {
                // Not importing categories, but still need to map IDs for rules
                for (var catImport : categoryImports) {
                    hibiscusToFintraxCategoryIdMap.put(catImport.hibiscusId, null);
                }
            }

            for (var txImport : transactionImports) {
                Long mappedAccountId = accountIdMap.get(txImport.accountHibiscusId);
                if (mappedAccountId == null) {
                    log.warn("Transaction {} references unknown account {}, skipping", txImport.hibiscusId, txImport.accountHibiscusId);
                    continue;
                }

                long newId = nextTransactionId(root);
                Transaction tx = Transaction.builder()
                        .id(newId)
                        .accountId(mappedAccountId)
                        .originalPayee(txImport.payee)
                        .payeeDisplay(txImport.payee)
                        .purpose(txImport.purpose)
                        .amount(txImport.amount)
                        .bookingDate(txImport.bookingDate)
                        .valueDate(txImport.valueDate)
                        .balanceAfter(txImport.balanceAfter)
                        .transactionType(txImport.transactionType)
                        .checksum(txImport.checksum)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                root.getTransactions().add(tx);
                transactionsCreated++;
            }

            if (importRules) {
                for (var catImport : categoryImports) {
                    if (catImport.pattern != null && !catImport.pattern.isEmpty()) {
                        Long mappedCategoryId = hibiscusToFintraxCategoryIdMap.get(catImport.hibiscusId);
                        if (mappedCategoryId == null) continue;

                        long ruleId = nextRuleId(root);
                        long condId = System.nanoTime();
                        long actionId = System.nanoTime() + 1;

                        Operator op = catImport.isRegex ? Operator.REGEX : Operator.CONTAINS;

                        RuleCondition condition = RuleCondition.builder()
                                .id(condId)
                                .ruleId(ruleId)
                                .matchField(MatchField.PAYEE_NAME)
                                .operator(op)
                                .value(catImport.pattern)
                                .build();

                        RuleAction action = RuleAction.builder()
                                .id(actionId)
                                .ruleId(ruleId)
                                .actionType(ActionType.ASSIGN_CATEGORY)
                                .categoryId(mappedCategoryId)
                                .build();

                        Rule rule = Rule.builder()
                                .id(ruleId)
                                .name("Imported: " + catImport.name)
                                .priority(root.getRules().size() + 1)
                                .enabled(true)
                                .conditions(List.of(condition))
                                .actions(List.of(action))
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                        root.getRules().add(rule);
                        rulesCreated++;
                    }
                }
            }

            for (var actImport : activityImports) {
                Long mappedAccountId = accountIdMap.get(actImport.accountHibiscusId);

                long newId = nextActivityId(root);
                ActivityLog activity = ActivityLog.builder()
                        .id(newId)
                        .timestamp(actImport.timestamp)
                        .action(actImport.action)
                        .entityType(EntityType.ACCOUNT)
                        .entityId(mappedAccountId)
                        .description(actImport.description)
                        .build();
                root.getActivityLogs().add(activity);
                activitiesCreated++;
            }

            storeManager.store(root.getAccounts());
            storeManager.store(root.getTransactions());
            storeManager.store(root.getCategories());
            storeManager.store(root.getRules());
            storeManager.store(root.getActivityLogs());

            ImportResult result = new ImportResult(
                    accountIdMap.size(), transactionsCreated, categoriesCreated,
                    rulesCreated, activitiesCreated);
            log.info("Import complete: {}", result);
            return result;

        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error("Failed to import Hibiscus XML", e);
            throw new RuntimeException("Failed to import Hibiscus XML: " + e.getMessage(), e);
        }
    }

    private void rejectDuplicateSourceCategories(List<CategoryImport> categoryImports) {
        Set<CategoryKey> seen = new HashSet<>();
        for (var catImport : categoryImports) {
            CategoryKey key = new CategoryKey(catImport.name, catImport.parentHibiscusId);
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate sibling category '" + catImport.name + "' in XML (parent hibiscus ID: "
                                + catImport.parentHibiscusId + ")");
            }
        }
    }

    private Optional<Category> findExistingFintraxCategory(DataRoot root, String name, Long parentFintraxId) {
        return root.getCategories().stream()
                .filter(c -> name.equals(c.getName()))
                .filter(c -> Objects.equals(parentFintraxId, c.getParentId()))
                .findFirst();
    }

    private Long resolveParentFintraxId(
            Map<Integer, Long> hibiscusToFintraxCategoryIdMap,
            Integer parentHibiscusId) {

        if (parentHibiscusId == null) {
            return null;
        }
        return hibiscusToFintraxCategoryIdMap.get(parentHibiscusId);
    }

    private boolean shouldSkipCategoryImport(
            CategoryImport hibiscusCategory,
            Long parentFintraxId,
            DataRoot root) {
        
        return findExistingFintraxCategory(root, hibiscusCategory.name, parentFintraxId).isPresent();
    }

    private void parseKonto(Element elem, int hibiscusId, Map<Integer, Long> accountIdMap) {
        DataRoot root = storeManager.getRoot();
        long newId = nextAccountId(root);

        String bezeichnung = getChildText(elem, "bezeichnung");
        String iban = getChildText(elem, "iban");
        String bic = getChildText(elem, "bic");
        String name = getChildText(elem, "name");
        String waehrung = getChildText(elem, "waehrung");
        String saldoStr = getChildText(elem, "saldo");
        String saldoDatumStr = getChildText(elem, "saldo_datum");

        BigDecimal balance = saldoStr != null ? new BigDecimal(saldoStr) : BigDecimal.ZERO;
        LocalDateTime balanceDate = saldoDatumStr != null ? parseDateTime(saldoDatumStr) : LocalDateTime.now();

        AccountType accountType = AccountType.GIRO;
        if (bezeichnung != null && bezeichnung.toLowerCase().contains("sparkonto")) {
            accountType = AccountType.SAVINGS;
        }

        BankAccount account = BankAccount.builder()
                .id(newId)
                .iban(iban)
                .bic(bic)
                .bankName(bezeichnung)
                .accountHolder(name)
                .accountType(accountType)
                .currency(waehrung != null ? waehrung : "EUR")
                .balance(balance)
                .balanceDate(balanceDate)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        root.getAccounts().add(account);
        accountIdMap.put(hibiscusId, newId);
        log.debug("Imported account {} (hibiscus {}) -> fintrax {}", bezeichnung, hibiscusId, newId);
    }

    private TransactionImport parseUmsatz(Element elem, int hibiscusId) {
        TransactionImport tx = new TransactionImport();
        tx.hibiscusId = hibiscusId;

        String datumStr = getChildText(elem, "datum");
        String valutaStr = getChildText(elem, "valuta");
        String empfaengerName = getChildText(elem, "empfaenger_name");
        String zweck = getChildText(elem, "zweck");
        String zweck2 = getChildText(elem, "zweck2");
        String zweck3 = getChildText(elem, "zweck3");
        String betragStr = getChildText(elem, "betrag");
        String saldoStr = getChildText(elem, "saldo");
        String art = getChildText(elem, "art");
        String checksumStr = getChildText(elem, "checksum");
        String kontoIdStr = getChildText(elem, "konto_id");

        tx.bookingDate = datumStr != null ? parseDate(datumStr) : LocalDate.now();
        tx.valueDate = valutaStr != null ? parseDate(valutaStr) : LocalDate.now();
        tx.payee = empfaengerName;
        tx.purpose = buildPurpose(zweck, zweck2, zweck3);
        tx.amount = betragStr != null ? new BigDecimal(betragStr) : BigDecimal.ZERO;
        tx.balanceAfter = saldoStr != null ? new BigDecimal(saldoStr) : null;
        tx.transactionType = art;
        tx.checksum = checksumStr;
        tx.accountHibiscusId = kontoIdStr != null ? Integer.parseInt(kontoIdStr) : null;

        return tx;
    }

    private CategoryImport parseUmsatzTyp(Element elem, int hibiscusId) {
        CategoryImport cat = new CategoryImport();
        cat.hibiscusId = hibiscusId;

        cat.name = getChildText(elem, "name");
        String parentIdStr = getChildText(elem, "parent_id");
        cat.parentHibiscusId = parentIdStr != null && !parentIdStr.isEmpty() ? Integer.parseInt(parentIdStr) : null;

        String customColorStr = getChildText(elem, "customcolor");
        if (customColorStr != null && !customColorStr.equals("0")) {
            cat.color = intToHexColor(Integer.parseInt(customColorStr));
        }

        cat.pattern = getChildText(elem, "pattern");
        String isRegexStr = getChildText(elem, "isregex");
        cat.isRegex = "1".equals(isRegexStr);

        return cat;
    }

    private ActivityLogImport parseProtokoll(Element elem, int hibiscusId) {
        ActivityLogImport act = new ActivityLogImport();

        String datumStr = getChildText(elem, "datum");
        String typStr = getChildText(elem, "typ");
        String kommentar = getChildText(elem, "kommentar");
        String kontoIdStr = getChildText(elem, "konto_id");

        act.timestamp = datumStr != null ? parseDateTime(datumStr) : LocalDateTime.now();
        act.action = mapProtokollTyp(typStr);
        act.description = kommentar;
        act.accountHibiscusId = kontoIdStr != null ? Integer.parseInt(kontoIdStr) : null;

        return act;
    }

    private String buildPurpose(String zweck, String zweck2, String zweck3) {
        StringBuilder sb = new StringBuilder();
        if (zweck != null && !zweck.isEmpty()) sb.append(zweck);
        if (zweck2 != null && !zweck2.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(zweck2);
        }
        if (zweck3 != null && !zweck3.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(zweck3);
        }
        return sb.toString();
    }

    private ActivityAction mapProtokollTyp(String typStr) {
        if (typStr == null) return ActivityAction.CREATE;
        return switch (typStr) {
            case "1" -> ActivityAction.CREATE;
            case "2" -> ActivityAction.UPDATE;
            case "3" -> ActivityAction.EXECUTE;
            default -> ActivityAction.CREATE;
        };
    }

    private String intToHexColor(int colorInt) {
        return String.format("#%06X", (0xFFFFFF & colorInt));
    }

    private LocalDateTime parseDateTime(String str) {
        try {
            return LocalDateTime.parse(str, DATE_TIME_FMT);
        } catch (Exception e) {
            log.warn("Failed to parse datetime: {}", str);
            return LocalDateTime.now();
        }
    }

    private LocalDate parseDate(String str) {
        try {
            String trimmed = str.trim();
            if (trimmed.length() > 10) {
                return LocalDate.parse(trimmed, DATE_FMT);
            }
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", str);
            return LocalDate.now();
        }
    }

    private String getChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private long nextAccountId(DataRoot root) {
        return root.getAccounts().stream().mapToLong(BankAccount::getId).max().orElse(0) + 1;
    }

    private long nextTransactionId(DataRoot root) {
        return root.getTransactions().stream().mapToLong(Transaction::getId).max().orElse(0) + 1;
    }

    private long nextCategoryId(DataRoot root) {
        return root.getCategories().stream().mapToLong(Category::getId).max().orElse(0) + 1;
    }

    private long nextRuleId(DataRoot root) {
        return root.getRules().stream().mapToLong(Rule::getId).max().orElse(0) + 1;
    }

    private long nextActivityId(DataRoot root) {
        return root.getActivityLogs().stream().mapToLong(ActivityLog::getId).max().orElse(0) + 1;
    }

    @Data
    public static class ImportResult {
        private final int accountsImported;
        private final int transactionsImported;
        private final int categoriesImported;
        private final int rulesImported;
        private final int activityLogsImported;

        public ImportResult(int accountsImported, int transactionsImported, int categoriesImported,
                            int rulesImported, int activityLogsImported) {
            this.accountsImported = accountsImported;
            this.transactionsImported = transactionsImported;
            this.categoriesImported = categoriesImported;
            this.rulesImported = rulesImported;
            this.activityLogsImported = activityLogsImported;
        }

        @Override
        public String toString() {
            return String.format("%d accounts, %d transactions, %d categories, %d rules, %d activity logs",
                    accountsImported, transactionsImported, categoriesImported,
                    rulesImported, activityLogsImported);
        }
    }

    private static class TransactionImport {
        int hibiscusId;
        Integer accountHibiscusId;
        String payee;
        String purpose;
        BigDecimal amount;
        LocalDate bookingDate;
        LocalDate valueDate;
        BigDecimal balanceAfter;
        String transactionType;
        String checksum;
    }

    private static class CategoryImport {
        int hibiscusId;
        Integer parentHibiscusId;
        String name;
        String color;
        String pattern;
        boolean isRegex;
    }

    private record CategoryKey(String name, Integer parentHibiscusId) {}

    private static class ActivityLogImport {
        Integer accountHibiscusId;
        LocalDateTime timestamp;
        ActivityAction action;
        String description;
    }
}
