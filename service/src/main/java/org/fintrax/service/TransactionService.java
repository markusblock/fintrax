package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransactionService {
    private final StoreManager store;
    private final ActivityLogger activityLogger;
    private long nextId = 1;

    public TransactionService(StoreManager store, ActivityLogger activityLogger) {
        this.store = store;
        this.activityLogger = activityLogger;
        initializeNextId();
    }

    private void initializeNextId() {
        nextId = store.getRoot().getTransactions().stream()
                .mapToLong(Transaction::getId)
                .max()
                .orElse(0) + 1;
    }

    public Transaction createTransaction(Transaction transaction) {
        if (transaction.getId() == null) {
            transaction.setId(nextId++);
        }
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        }
        transaction.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));

        store.getRoot().getTransactions().add(transaction);
        store.store(store.getRoot().getTransactions());

        log.debug("Created transaction {}", transaction.getId());
        return transaction;
    }

    public Transaction updateFromBank(Transaction stored, Transaction received) {
        stored.setAccountId(received.getAccountId());
        if (received.getOriginalPayee() != null) stored.setOriginalPayee(received.getOriginalPayee());
        if (received.getPurpose() != null && !received.getPurpose().isBlank()) stored.setPurpose(received.getPurpose());
        if (received.getAmount() != null) stored.setAmount(received.getAmount());
        if (received.getBookingDate() != null) stored.setBookingDate(received.getBookingDate());
        if (received.getValueDate() != null) stored.setValueDate(received.getValueDate());
        if (received.getBalanceAfter() != null) stored.setBalanceAfter(received.getBalanceAfter());
        if (received.getTransactionType() != null) stored.setTransactionType(received.getTransactionType());
        if (received.getChecksum() != null) stored.setChecksum(received.getChecksum());
        if (received.getEndToEndId() != null) stored.setEndToEndId(received.getEndToEndId());
        if (received.getMandateId() != null) stored.setMandateId(received.getMandateId());
        if (received.getCreditorId() != null) stored.setCreditorId(received.getCreditorId());
        if (received.getCustomerReference() != null) stored.setCustomerReference(received.getCustomerReference());
        if (received.getPrimanota() != null) stored.setPrimanota(received.getPrimanota());
        if (received.getGvCode() != null) stored.setGvCode(received.getGvCode());
        stored.setBooked(stored.isBooked() || received.isBooked());
        stored.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        store.store(stored);
        return stored;
    }

    public void deleteTransactions(Collection<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return;
        }
        store.getRoot().getTransactions().removeIf(transaction -> transactionIds.contains(transaction.getId()));
        store.store(store.getRoot().getTransactions());
    }

    public Optional<Transaction> getTransaction(Long id) {
        return Optional.ofNullable(store.getTransaction(id));
    }

    public List<Transaction> getTransactionsByAccount(Long accountId) {
        return store.getRoot().getTransactions().stream()
                .filter(t -> t.getAccountId().equals(accountId))
                .toList();
    }

    public List<Transaction> getAllTransactions() {
        return List.copyOf(store.getRoot().getTransactions());
    }

    public List<Transaction> filterTransactions(TransactionFilter filter) {
        return filterTransactions(store.getRoot().getTransactions(), filter);
    }

    public List<Transaction> filterTransactions(Collection<Transaction> transactions, TransactionFilter filter) {
        Set<Long> matchingCategoryIds = matchingCategoryIds(filter.categoryId());
        Map<Long, String> categoryNames = store.getRoot().getCategories().stream()
                .filter(category -> category.getName() != null)
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, String> labelNames = store.getRoot().getLabels().stream()
                .filter(label -> label.getName() != null)
                .collect(Collectors.toMap(Label::getId, Label::getName));
        DateTimeFormatter searchDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(I18n.getCurrentLocale());

        return transactions.stream()
                .filter(transaction -> matchesAccount(transaction, filter))
                .filter(transaction -> matchesDateFilter(transaction, filter))
                .filter(transaction -> matchesCategory(transaction, filter, matchingCategoryIds))
                .filter(transaction -> matchesLabels(transaction, filter))
                .filter(transaction -> matchesAmount(transaction, filter))
                .filter(transaction -> matchesSearchFilter(transaction, filter, categoryNames, labelNames,
                        searchDateFormatter))
                .toList();
    }

    private Set<Long> matchingCategoryIds(Long categoryId) {
        Set<Long> matchingCategoryIds = new HashSet<>();
        if (categoryId != null) {
            matchingCategoryIds.add(categoryId);
            store.getRoot().getCategories().stream()
                    .filter(category -> categoryId.equals(category.getParentId()))
                    .map(Category::getId)
                    .forEach(matchingCategoryIds::add);
        }
        return matchingCategoryIds;
    }

    private boolean matchesAccount(Transaction transaction, TransactionFilter filter) {
        return filter.accountId() == null || transaction.getAccountId().equals(filter.accountId());
    }

    private boolean matchesDateFilter(Transaction transaction, TransactionFilter filter) {
        return filter.fromDate() == null && filter.toDate() == null
                || matchesDateRange(transaction.getBookingDate(), filter);
    }

    private boolean matchesCategory(Transaction transaction, TransactionFilter filter,
                                    Set<Long> matchingCategoryIds) {
        if (filter.noCategory()) return transaction.getCategoryId() == null;
        return filter.categoryId() == null || matchingCategoryIds.contains(transaction.getCategoryId());
    }

    private boolean matchesLabels(Transaction transaction, TransactionFilter filter) {
        Set<Long> labelIds = transaction.getLabelIds();
        if (filter.noLabels()) return labelIds == null || labelIds.isEmpty();
        Set<Long> requiredLabelIds = filter.labelIds();
        return requiredLabelIds == null || requiredLabelIds.isEmpty()
                || (labelIds != null && labelIds.containsAll(requiredLabelIds));
    }

    private boolean matchesAmount(Transaction transaction, TransactionFilter filter) {
        BigDecimal amount = transaction.getAmount();
        if (filter.minAmount() != null && (amount == null || amount.compareTo(filter.minAmount()) < 0)) {
            return false;
        }
        return filter.maxAmount() == null || amount != null && amount.compareTo(filter.maxAmount()) <= 0;
    }

    private boolean matchesSearchFilter(Transaction transaction, TransactionFilter filter,
                                        Map<Long, String> categoryNames, Map<Long, String> labelNames,
                                        DateTimeFormatter searchDateFormatter) {
        String searchText = filter.searchText();
        return searchText == null || searchText.isEmpty()
                || matchesSearch(transaction, searchText, categoryNames, labelNames, searchDateFormatter);
    }

    private boolean matchesDateRange(LocalDate date, TransactionFilter filter) {
        return date != null
                && (filter.fromDate() == null || !date.isBefore(filter.fromDate()))
                && (filter.toDate() == null || !date.isAfter(filter.toDate()));
    }

    private boolean matchesSearch(Transaction transaction, String searchText,
                                  Map<Long, String> categoryNames, Map<Long, String> labelNames,
                                  DateTimeFormatter searchDateFormatter) {
        String normalizedSearchText = searchText.toLowerCase(Locale.ROOT);
        if (containsSearch(transaction.getOriginalPayee(), normalizedSearchText)
                || containsSearch(transaction.getPayeeDisplay(), normalizedSearchText)
                || containsSearch(transaction.getPurpose(), normalizedSearchText)
                || containsSearch(transaction.getNote(), normalizedSearchText)
                || containsSearch(categoryNames.get(transaction.getCategoryId()), normalizedSearchText)) {
            return true;
        }

        if (transaction.getLabelIds() != null && transaction.getLabelIds().stream()
                .map(labelNames::get)
                .anyMatch(labelName -> containsSearch(labelName, normalizedSearchText))) {
            return true;
        }

        BigDecimal amount = transaction.getAmount();
        if (amount != null && (containsSearch(amount.toPlainString(), normalizedSearchText)
                || containsSearch(String.format("%,.2f EUR", amount.doubleValue()), normalizedSearchText))) {
            return true;
        }

        LocalDate bookingDate = transaction.getBookingDate();
        return bookingDate != null && (containsSearch(bookingDate.toString(), normalizedSearchText)
                || containsSearch(searchDateFormatter.format(bookingDate), normalizedSearchText));
    }

    private boolean containsSearch(String value, String normalizedSearchText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearchText);
    }

    public Transaction updateCategory(Long transactionId, Long categoryId) {
        Transaction transaction = store.getTransaction(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        transaction.setCategoryId(categoryId);
        transaction.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));

        store.store(transaction);

        activityLogger.log(ActivityAction.UPDATE, EntityType.TRANSACTION, transactionId,
                "Updated category to " + categoryId);

        return transaction;
    }

    public Transaction updateLabels(Long transactionId, Set<Long> labelIds) {
        Transaction transaction = store.getTransaction(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        transaction.setLabelIds(labelIds);
        transaction.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));

        store.store(transaction);

        activityLogger.log(ActivityAction.UPDATE, EntityType.TRANSACTION, transactionId,
                "Updated labels");

        return transaction;
    }

    public Transaction updateNote(Long transactionId, String note) {
        Transaction transaction = store.getTransaction(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        transaction.setNote(note);
        transaction.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));

        store.store(transaction);

        activityLogger.log(ActivityAction.UPDATE, EntityType.TRANSACTION, transactionId,
                "Updated note");

        return transaction;
    }

    public Transaction updatePayeeDisplay(Long transactionId, String payeeDisplay) {
        Transaction transaction = store.getTransaction(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        transaction.setPayeeDisplay(payeeDisplay);
        transaction.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));

        store.store(transaction);

        activityLogger.log(ActivityAction.UPDATE, EntityType.TRANSACTION, transactionId,
                "Updated payee display name");

        return transaction;
    }

    public void bulkUpdateCategory(List<Long> transactionIds, Long categoryId) {
        for (Long id : transactionIds) {
            updateCategory(id, categoryId);
        }
        log.info("Bulk updated category for {} transactions", transactionIds.size());
    }

    public void bulkAddLabel(List<Long> transactionIds, Long labelId) {
        for (Long id : transactionIds) {
            Transaction transaction = store.getTransaction(id);
            if (transaction != null) {
                Set<Long> labelIds = transaction.getLabelIds() == null
                        ? new HashSet<>()
                        : new HashSet<>(transaction.getLabelIds());
                labelIds.add(labelId);
                transaction.setLabelIds(labelIds);
                transaction.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
                store.store(transaction);
            }
        }
        log.info("Bulk added label {} to {} transactions", labelId, transactionIds.size());
    }

    public void bulkRemoveLabel(List<Long> transactionIds, Long labelId) {
        for (Long id : transactionIds) {
            Transaction transaction = store.getTransaction(id);
            if (transaction != null) {
                Set<Long> labelIds = transaction.getLabelIds() == null
                        ? new HashSet<>()
                        : new HashSet<>(transaction.getLabelIds());
                labelIds.remove(labelId);
                transaction.setLabelIds(labelIds);
                transaction.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
                store.store(transaction);
            }
        }
        log.info("Bulk removed label {} from {} transactions", labelId, transactionIds.size());
    }
}
