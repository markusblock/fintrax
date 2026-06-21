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
public class SyncLog {
    private Long id;
    private Long bankAccountId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private SyncStatus status;
    @Builder.Default
    private int newCount = 0;
    @Builder.Default
    private int skippedCount = 0;
    private String errorMessage;
}
