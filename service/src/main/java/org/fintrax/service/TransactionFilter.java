package org.fintrax.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record TransactionFilter(Long accountId, LocalDate fromDate, LocalDate toDate, Long categoryId,
                                boolean noCategory, Set<Long> labelIds, boolean noLabels,
                                BigDecimal minAmount, BigDecimal maxAmount, String searchText) {
    public TransactionFilter(Long accountId, LocalDate fromDate, LocalDate toDate, Long categoryId,
                             Set<Long> labelIds, BigDecimal minAmount, BigDecimal maxAmount,
                             String searchText) {
        this(accountId, fromDate, toDate, categoryId, false, labelIds, false,
                minAmount, maxAmount, searchText);
    }
}
