package org.fintrax.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {
    private Long id;
    private String iban;
    private String bic;
    private String bankName;
    private String accountHolder;
    private AccountType accountType;
    @Builder.Default
    private String currency = "EUR";
    private BigDecimal balance;
    private LocalDateTime balanceDate;
    private LocalDateTime lastSyncAt;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
