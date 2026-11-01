package org.fintrax.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record TransactionFilter(Long accountId, LocalDate fromDate, LocalDate toDate, Long categoryId,
                                Set<Long> labelIds, BigDecimal minAmount, BigDecimal maxAmount,
                                String searchText) {
}
