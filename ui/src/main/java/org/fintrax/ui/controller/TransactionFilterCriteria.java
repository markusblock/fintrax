package org.fintrax.ui.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

record TransactionFilterCriteria(Long accountId, Long categoryId, LocalDate fromDate, LocalDate toDate,
                                 BigDecimal minAmount, BigDecimal maxAmount, String searchText) {
}
