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
public class ActivityLog {
    private Long id;
    private LocalDateTime timestamp;
    private ActivityAction action;
    private EntityType entityType;
    private Long entityId;
    private String description;
}
