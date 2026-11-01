package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
            transaction.setCreatedAt(LocalDateTime.now());
        }
        transaction.setUpdatedAt(LocalDateTime.now());

        store.getRoot().getTransactions().add(transaction);
        store.store(store.getRoot().getTransactions());

        log.debug("Created transaction {}", transaction.getId());
        return transaction;
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
        return transactions.stream()
                .filter(t -> filter.accountId() == null || t.getAccountId().equals(filter.accountId()))
                .filter(t -> filter.fromDate() == null || (t.getBookingDate() != null &&
                        !t.getBookingDate().isBefore(filter.fromDate())))
                .filter(t -> filter.toDate() == null || (t.getBookingDate() != null &&
                        !t.getBookingDate().isAfter(filter.toDate())))
                .filter(t -> filter.categoryId() == null || filter.categoryId().equals(t.getCategoryId()))
                .filter(t -> filter.labelIds() == null || filter.labelIds().isEmpty() ||
                        t.getLabelIds().containsAll(filter.labelIds()))
                .filter(t -> filter.minAmount() == null || t.getAmount().compareTo(filter.minAmount()) >= 0)
                .filter(t -> filter.maxAmount() == null || t.getAmount().compareTo(filter.maxAmount()) <= 0)
                .filter(t -> filter.searchText() == null || filter.searchText().isEmpty()
                        || matchesSearch(t, filter.searchText()))
                .toList();
    }

    private boolean matchesSearch(Transaction t, String searchText) {
        String lower = searchText.toLowerCase();
        return (t.getOriginalPayee() != null && t.getOriginalPayee().toLowerCase().contains(lower)) ||
                (t.getPayeeDisplay() != null && t.getPayeeDisplay().toLowerCase().contains(lower)) ||
                (t.getPurpose() != null && t.getPurpose().toLowerCase().contains(lower)) ||
                (t.getNote() != null && t.getNote().toLowerCase().contains(lower));
    }

    public Transaction updateCategory(Long transactionId, Long categoryId) {
        Transaction transaction = store.getTransaction(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        transaction.setCategoryId(categoryId);
        transaction.setUpdatedAt(LocalDateTime.now());

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
        transaction.setUpdatedAt(LocalDateTime.now());

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
        transaction.setUpdatedAt(LocalDateTime.now());

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
        transaction.setUpdatedAt(LocalDateTime.now());

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
                transaction.setUpdatedAt(LocalDateTime.now());
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
                transaction.setUpdatedAt(LocalDateTime.now());
                store.store(transaction);
            }
        }
        log.info("Bulk removed label {} from {} transactions", labelId, transactionIds.size());
    }
}
