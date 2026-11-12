package org.fintrax.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bank {
    private Long id;
    private String name;
    private String bic;
    private String blz;
    private String fintsServerUrl;
    private String fintsUserId;
    private String fintsCustomerId;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
