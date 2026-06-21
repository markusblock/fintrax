package org.fintrax.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleCondition {
    private Long id;
    private Long ruleId;
    private MatchField matchField;
    private Operator operator;
    private String value;
    private java.math.BigDecimal amountMin;
    private java.math.BigDecimal amountMax;
    private Long bankAccountId;
}
