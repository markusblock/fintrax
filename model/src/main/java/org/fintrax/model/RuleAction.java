package org.fintrax.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleAction {
    private Long id;
    private Long ruleId;
    private ActionType actionType;
    private Long categoryId;
    private Long labelId;
    private String note;
}
