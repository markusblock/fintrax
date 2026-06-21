package org.fintrax.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    private Long id;
    private Long accountId;
    private String originalPayee;
    private String payeeDisplay;
    private String purpose;
    private BigDecimal amount;
    private LocalDate bookingDate;
    private LocalDate valueDate;
    private BigDecimal balanceAfter;
    private String transactionType;
    private String checksum;
    private Long categoryId;
    private String note;
    @Builder.Default
    private Set<Long> labelIds = new HashSet<>();
    private String endToEndId;
    private String mandateId;
    private String creditorId;
    @Builder.Default
    private boolean booked = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
