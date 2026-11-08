package org.fintrax.ui.controller;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
public class SessionViewState {
    static final String TRANSACTIONS_VIEW = "transactions";
    static final String CLASSIFICATIONS_VIEW = "classifications";
    static final String RULES_VIEW = "rules";

    private final Map<String, TransactionFilterPanelState> transactionFilters = new HashMap<>();
    private String ruleSearchText = "";
    private Long selectedRuleId;
    private ClassificationSelection classificationSelection;

    public void clear() {
        transactionFilters.clear();
        ruleSearchText = "";
        selectedRuleId = null;
        classificationSelection = null;
    }

    TransactionFilterPanelState transactionFilters(String viewKey) {
        return transactionFilters.get(viewKey);
    }

    void saveTransactionFilters(String viewKey, TransactionFilterPanelState state) {
        transactionFilters.put(viewKey, state);
    }

    String ruleSearchText() {
        return ruleSearchText;
    }

    void setRuleSearchText(String ruleSearchText) {
        this.ruleSearchText = ruleSearchText == null ? "" : ruleSearchText;
    }

    Long selectedRuleId() {
        return selectedRuleId;
    }

    void setSelectedRuleId(Long selectedRuleId) {
        this.selectedRuleId = selectedRuleId;
    }

    ClassificationSelection classificationSelection() {
        return classificationSelection;
    }

    void setClassificationSelection(ClassificationSelection classificationSelection) {
        this.classificationSelection = classificationSelection;
    }

    record TransactionFilterPanelState(Long accountId, Long categoryId, String searchText,
                                       String minAmountText, String maxAmountText,
                                       TransactionDateFilters.Preset datePreset,
                                       LocalDate fromDate, String invalidFromDateText,
                                       LocalDate toDate, String invalidToDateText,
                                       boolean additionalFiltersExpanded) {
    }

    record ClassificationSelection(Long categoryId, Long labelId) {
        ClassificationSelection {
            if (categoryId != null && labelId != null) {
                throw new IllegalArgumentException("Selection cannot be both a category and a label");
            }
        }
    }
}
