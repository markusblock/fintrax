package org.fintrax.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rule {
    private Long id;
    private String name;
    private int priority;
    @Builder.Default
    private boolean enabled = true;
    @Builder.Default
    private List<RuleCondition> conditions = new ArrayList<>();
    @Builder.Default
    private List<RuleAction> actions = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
