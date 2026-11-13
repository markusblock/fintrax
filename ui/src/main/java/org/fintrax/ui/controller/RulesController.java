package org.fintrax.ui.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.model.*;
import org.fintrax.service.*;
import org.fintrax.ui.FintraxUI;
import org.fintrax.ui.ViewLoader;
import org.fintrax.ui.control.TableCellTooltips;
import org.fintrax.ui.control.TableColumnConfig;
import org.fintrax.ui.control.TransactionTableSetup;
import org.fintrax.ui.control.TableViewResizePolicy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope("prototype")
public class RulesController {
    private static final String COLUMN_CONFIG_KEY = "rules.transaction.columns";
    private static final String AMOUNT_POSITIVE_STYLE = "amount-positive";
    private static final String AMOUNT_NEGATIVE_STYLE = "amount-negative";
    private static final String DISABLED_RULE_ROW_CLASS = "disabled-rule-row";
    private static final String WARNING_COPY_STYLE = "warning-copy";

    @FXML
    private BorderPane rootPane;
    @FXML
    private TableView<Rule> ruleTable;
    @FXML
    private TextField ruleSearchField;
    @FXML
    private Button ruleSearchClearButton;
    @FXML
    private TableColumn<Rule, Number> ruleNumberColumn;
    @FXML
    private TableColumn<Rule, String> ruleNameColumn;
    @FXML
    private Label ruleNameSummary;
    @FXML
    private Label ruleConditionsSummary;
    @FXML
    private Label ruleActionsSummary;
    @FXML
    private Label ruleMatchSummary;
    @FXML
    private Label ruleConflictSummary;
    @FXML
    private TableView<Transaction> ruleTransactionsTable;
    @FXML
    private TableColumn<Transaction, Number> transactionRuleCountColumn;
    @FXML
    private TableColumn<Transaction, String> transactionMatchedRulesColumn;
    @FXML
    private Label ruleTransactionsTotalAmount;
    @FXML
    private TransactionFilterPanelController transactionFilterPanelController;

    private final RuleService ruleService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final SettingsService settingsService;
    private final TransactionService transactionService;
    private final TransactionTableActions transactionTableActions;
    private final ViewLoader viewLoader;
    private final SessionViewState sessionViewState;

    private final ObservableList<Rule> ruleData = FXCollections.observableArrayList();
    private final ObservableList<Transaction> displayedTransactions = FXCollections.observableArrayList();
    private TransactionTableSetup transactionTableSetup;
    private Long selectedRuleId;
    private List<Transaction> selectedRuleTransactions = List.of();
    private Map<Long, List<RuleMatchSummary.MatchingRule>> matchingRulesByTransactionId = Map.of();

    public RulesController(RuleService ruleService, AccountService accountService,
                           CategoryService categoryService, LabelService labelService,
                           SettingsService settingsService, TransactionService transactionService,
                           TransactionTableActions transactionTableActions, ViewLoader viewLoader,
                           SessionViewState sessionViewState) {
        this.ruleService = ruleService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.settingsService = settingsService;
        this.transactionService = transactionService;
        this.transactionTableActions = transactionTableActions;
        this.viewLoader = viewLoader;
        this.sessionViewState = sessionViewState;
    }

    @FXML
    public void initialize() {
        log.info("RulesController initialized");
        ruleNumberColumn.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().getPriority()));
        ruleNameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getName()));
        ruleNumberColumn.setSortable(true);
        ruleNameColumn.setSortable(true);
        ruleNameColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        FilteredList<Rule> filteredRules = new FilteredList<>(ruleData);
        String previousSearch = sessionViewState.ruleSearchText();
        ruleSearchField.setText(previousSearch);
        filteredRules.setPredicate(ruleNamePredicate(previousSearch));
        ruleSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredRules.setPredicate(ruleNamePredicate(newValue));
            sessionViewState.setRuleSearchText(newValue);
        });
        ruleSearchClearButton.visibleProperty().bind(ruleSearchField.textProperty().isNotEmpty());
        ruleSearchClearButton.managedProperty().bind(ruleSearchClearButton.visibleProperty());
        SortedList<Rule> sortedRules = new SortedList<>(filteredRules);
        sortedRules.comparatorProperty().bind(ruleTable.comparatorProperty());
        ruleTable.setItems(sortedRules);
        ruleNumberColumn.setSortType(TableColumn.SortType.ASCENDING);
        ruleTable.getSortOrder().setAll(ruleNumberColumn);
        ruleTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableViewResizePolicy.fitColumnsAndAllowHorizontalScrolling(ruleTable);
        TableCellTooltips.install(ruleTable);
        ruleTable.setRowFactory(table -> {
            TableRow<Rule> row = new TableRow<>() {
                @Override
                protected void updateItem(Rule item, boolean empty) {
                    super.updateItem(item, empty);
                    setStyle("");
                    getStyleClass().remove(DISABLED_RULE_ROW_CLASS);
                    if (empty || item == null) {
                        setContextMenu(null);
                    } else {
                        if (!item.isEnabled()) getStyleClass().add(DISABLED_RULE_ROW_CLASS);
                        setContextMenu(createRuleContextMenu(item));
                    }
                }
            };
            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty() && !ruleTable.getSelectionModel().isSelected(row.getIndex())) {
                    ruleTable.getSelectionModel().clearAndSelect(row.getIndex());
                }
            });
            return row;
        });
        loadRules();
        setupTransactionTable();
        setupRuleMatchColumns();
        TableCellTooltips.install(ruleTransactionsTable);
        ruleTransactionsTable.setItems(displayedTransactions);
        setupColumnMenu();
        displayedTransactions.addListener((javafx.collections.ListChangeListener<Transaction>) change ->
                updateTransactionTotal());
        updateTransactionTotal();
        transactionTableActions.installContextMenu(
                ruleTransactionsTable, this::reloadSelectedTransactionsKeepingSelection, null);
        transactionFilterPanelController.setCollapsible(true);
        transactionFilterPanelController.restoreForView(
                SessionViewState.RULES_VIEW, TransactionDateFilters.Preset.ALL_TIME);
        transactionFilterPanelController.setOnFiltersChanged(this::refreshSelectedRuleTransactions);
        ruleTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRule, newRule) ->
                showSelectedRule(newRule));
        restoreSelectedRule();
    }

    private void setupColumnMenu() {
        transactionTableSetup.installColumnMenu(COLUMN_CONFIG_KEY, settingsService, List.of(
                new TableColumnConfig("matchingRuleCount", transactionRuleCountColumn),
                new TableColumnConfig("matchingRules", transactionMatchedRulesColumn)));
    }

    private void setupTransactionTable() {
        transactionTableSetup = new TransactionTableSetup(ruleTransactionsTable,
                accountService, categoryService, labelService);
        transactionTableSetup.configure();
    }

    private void setupRuleMatchColumns() {
        transactionRuleCountColumn.setCellValueFactory(cell -> new SimpleIntegerProperty(
                matchingRulesByTransactionId.getOrDefault(cell.getValue().getId(), List.of()).size()));
        transactionMatchedRulesColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                matchingRulesByTransactionId.getOrDefault(cell.getValue().getId(), List.of()).stream()
                        .map(matchingRule -> I18n.get("rules.ruleReference",
                                matchingRule.name(), matchingRule.priority()))
                        .collect(Collectors.joining(", "))));
        transactionRuleCountColumn.setSortable(true);
        transactionMatchedRulesColumn.setSortable(true);
    }

    @FXML
    private void onClearRuleSearch() {
        ruleSearchField.clear();
        ruleSearchField.requestFocus();
    }

    private Predicate<Rule> ruleNamePredicate(String searchText) {
        String query = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        return rule -> query.isEmpty() || rule.getName().toLowerCase(Locale.ROOT).contains(query);
    }

    private ContextMenu createRuleContextMenu(Rule rule) {
        MenuItem editRule = new MenuItem(I18n.get("button.edit"));
        editRule.setGraphic(icon("fth-edit-2", 14));
        editRule.setOnAction(event -> editRule(rule));

        MenuItem mergeRules = new MenuItem(I18n.get("rules.mergeSelected"));
        mergeRules.setOnAction(event -> mergeSelectedRules());

        MenuItem applyRule = new MenuItem(I18n.get("rules.applySelected"));
        applyRule.setGraphic(icon("fth-play", 14));
        applyRule.setDisable(!rule.isEnabled());
        applyRule.setOnAction(event -> new RuleReviewDialog(rootPane.getScene().getWindow(),
                new RuleReviewDialog.Services(ruleService, accountService, categoryService, labelService,
                        settingsService),
                onlyFillMissing -> ruleService.previewSingleRule(rule.getId(), onlyFillMissing), true).show());

        int ruleIndex = indexOfRule(rule.getId());
        MenuItem moveUp = new MenuItem(I18n.get("button.moveUp"));
        moveUp.setGraphic(icon("fth-arrow-up", 14));
        moveUp.setDisable(ruleIndex <= 0);
        moveUp.setOnAction(event -> moveRule(rule, -1));
        MenuItem moveDown = new MenuItem(I18n.get("button.moveDown"));
        moveDown.setGraphic(icon("fth-arrow-down", 14));
        moveDown.setDisable(ruleIndex < 0 || ruleIndex >= ruleData.size() - 1);
        moveDown.setOnAction(event -> moveRule(rule, 1));

        MenuItem toggleEnabled = new MenuItem(I18n.get(rule.isEnabled() ? "rules.disable" : "rules.enable"));
        toggleEnabled.setGraphic(icon("fth-toggle-right", 14));
        toggleEnabled.setOnAction(event -> toggleRule(rule));

        MenuItem deleteRule = new MenuItem(I18n.get("button.delete"));
        deleteRule.setGraphic(icon("fth-trash-2", 14));
        deleteRule.setOnAction(event -> confirmDeleteRule(rule));

        ContextMenu contextMenu = new ContextMenu(editRule, mergeRules, applyRule,
                new SeparatorMenuItem(), moveUp, moveDown, toggleEnabled,
                new SeparatorMenuItem(), deleteRule);
        contextMenu.setOnShowing(event -> {
            int selectedCount = ruleTable.getSelectionModel().getSelectedItems().size();
            editRule.setDisable(selectedCount != 1);
            mergeRules.setDisable(selectedCount == 0 || (selectedCount == 1 && ruleData.size() < 2));
        });
        return contextMenu;
    }

    private void loadRules() {
        ruleData.setAll(ruleService.getAllRules());
        ruleTable.sort();
        ruleTable.refresh();
    }

    private void showSelectedRule(Rule rule) {
        if (rule == null) {
            clearSelectedRule();
            return;
        }
        selectedRuleId = rule.getId();
        sessionViewState.setSelectedRuleId(selectedRuleId);
        ruleNameSummary.setText(rule.getName());
        String conditionSummaryKey = rule.getMatchMode() == RuleMatchMode.ANY
                ? "rules.summary.conditionsAny" : "rules.summary.conditionsAll";
        ruleConditionsSummary.setText(I18n.get(conditionSummaryKey, rule.getConditions().stream()
                .map(this::conditionSummary).collect(Collectors.joining(" · "))));
        ruleActionsSummary.setText(I18n.get("rules.summary.actions", rule.getActions().stream()
                .map(this::actionSummary)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" · "))));
        updateRuleMatchSummary(rule);
        reloadSelectedRuleTransactions();
        refreshSelectedRuleTransactions();
    }

    private void reloadSelectedRuleTransactions() {
        selectedRuleTransactions = selectedRuleId == null
                ? List.of() : ruleService.getMatchingTransactions(selectedRuleId);
        matchingRulesByTransactionId = ruleService.getMatchingRulesForTransactions(
                selectedRuleTransactions.stream().map(Transaction::getId).toList());
    }

    private void clearSelectedRule() {
        selectedRuleId = null;
        sessionViewState.setSelectedRuleId(null);
        selectedRuleTransactions = List.of();
        ruleNameSummary.setText(I18n.get("rules.selectRule"));
        ruleConditionsSummary.setText("");
        ruleActionsSummary.setText("");
        ruleMatchSummary.setText("");
        ruleConflictSummary.setText("");
        ruleConflictSummary.setVisible(false);
        ruleConflictSummary.setManaged(false);
        ruleConflictSummary.getStyleClass().remove(WARNING_COPY_STYLE);
        matchingRulesByTransactionId = Map.of();
        displayedTransactions.clear();
    }

    private void updateRuleMatchSummary(Rule rule) {
        RuleMatchSummary summary = ruleService.getMatchSummary(rule.getId());
        ruleMatchSummary.setText(I18n.get("rules.matchCount", summary.matchingTransactionCount()));
        boolean hasConflicts = summary.conflictingTransactionCount() > 0;
        String conflictingRules = summary.conflictingRules().stream()
                .map(conflictingRule -> I18n.get("rules.ruleReference",
                        conflictingRule.name(), conflictingRule.priority()))
                .collect(Collectors.joining(", "));
        ruleConflictSummary.setText(hasConflicts
                ? I18n.get("rules.conflictCount", summary.conflictingTransactionCount(), conflictingRules)
                : "");
        ruleConflictSummary.setVisible(hasConflicts);
        ruleConflictSummary.setManaged(hasConflicts);
        if (hasConflicts) {
            if (!ruleConflictSummary.getStyleClass().contains(WARNING_COPY_STYLE)) {
                ruleConflictSummary.getStyleClass().add(WARNING_COPY_STYLE);
            }
        } else {
            ruleConflictSummary.getStyleClass().remove(WARNING_COPY_STYLE);
        }
    }

    private void restoreSelectedRule() {
        Long previousRuleId = sessionViewState.selectedRuleId();
        Rule selectedRule = previousRuleId == null ? null : ruleData.stream()
                .filter(rule -> Objects.equals(rule.getId(), previousRuleId))
                .filter(ruleTable.getItems()::contains)
                .findFirst().orElse(null);
        if (selectedRule == null) {
            clearSelectedRule();
            return;
        }
        ruleTable.getSelectionModel().select(selectedRule);
    }

    private String conditionSummary(RuleCondition condition) {
        String value = condition.getOperator() == Operator.AMOUNT_RANGE
                ? amountRangeSummary(condition)
                : Objects.toString(condition.getValue(), "");
        return fieldText(condition.getMatchField()) + " " + operatorText(condition.getOperator()) + " " + value;
    }

    private String fieldText(MatchField field) {
        return I18n.get("ruleEditor.field." + switch (field) {
            case PAYEE_NAME -> "payeeName";
            case PAYEE_OR_PURPOSE -> "payeeOrPurpose";
            case PURPOSE -> "purpose";
            case AMOUNT -> "amount";
            case ACCOUNT -> "account";
            case TRANSACTION_TYPE -> "transactionType";
        });
    }

    private String operatorText(Operator operator) {
        return I18n.get("ruleEditor.operator." + switch (operator) {
            case CONTAINS -> "contains";
            case CONTAINS_NOT -> "containsNot";
            case EQUALS -> "is";
            case NOT_EQUALS -> "isNot";
            case REGEX -> "matchesRegex";
            case NOT_REGEX -> "doesNotMatchRegex";
            case AMOUNT_RANGE -> "amountRange";
        });
    }

    private String amountRangeSummary(RuleCondition condition) {
        String minimum = condition.getAmountMin() == null ? "−∞" : condition.getAmountMin().toPlainString();
        String maximum = condition.getAmountMax() == null ? "∞" : condition.getAmountMax().toPlainString();
        return minimum + "–" + maximum;
    }

    private String actionSummary(RuleAction action) {
        return switch (action.getActionType()) {
            case ASSIGN_CATEGORY -> categoryService.getCategory(action.getCategoryId())
                    .map(category -> I18n.get("rules.summary.assignCategory", category.getName()))
                    .orElse(I18n.get("rules.summary.unknownTarget"));
            case ADD_LABEL -> labelService.getLabel(action.getLabelId())
                    .map(label -> I18n.get("rules.summary.addLabel", label.getName()))
                    .orElse(I18n.get("rules.summary.unknownTarget"));
            case SET_NOTE -> null;
        };
    }

    private void refreshSelectedRuleTransactions() {
        if (selectedRuleId == null) {
            displayedTransactions.clear();
            return;
        }
        Optional<TransactionFilter> criteria = transactionFilterPanelController.getCriteria();
        if (criteria.isEmpty()) {
            displayedTransactions.clear();
            return;
        }
        displayedTransactions.setAll(transactionService.filterTransactions(selectedRuleTransactions, criteria.get()));
        if (!ruleTransactionsTable.getSortOrder().isEmpty()) ruleTransactionsTable.sort();
    }

    private void reloadSelectedTransactionsKeepingSelection() {
        Set<Long> selectedIds = ruleTransactionsTable.getSelectionModel().getSelectedItems().stream()
                .map(Transaction::getId)
                .collect(Collectors.toSet());
        if (selectedRuleId != null) {
            reloadSelectedRuleTransactions();
        }
        refreshSelectedRuleTransactions();
        for (int index = 0; index < displayedTransactions.size(); index++) {
            if (selectedIds.contains(displayedTransactions.get(index).getId())) {
                ruleTransactionsTable.getSelectionModel().select(index);
            }
        }
    }

    private void updateTransactionTotal() {
        BigDecimal total = displayedTransactions.stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ruleTransactionsTotalAmount.setText(formatAmount(total));
        ruleTransactionsTotalAmount.getStyleClass().removeAll(AMOUNT_POSITIVE_STYLE, AMOUNT_NEGATIVE_STYLE);
        ruleTransactionsTotalAmount.getStyleClass()
                .add(total.signum() < 0 ? AMOUNT_NEGATIVE_STYLE : AMOUNT_POSITIVE_STYLE);
    }

    private String formatAmount(BigDecimal amount) {
        return String.format("%,.2f EUR", amount);
    }

    @FXML
    private void onAddRule() {
        RuleEditorDialog.show(rootPane.getScene().getWindow(), viewLoader,
                        "dialog.newRule.title", "dialog.newRule.header",
                        "createRuleSave", "createRuleCancel", RuleEditorController::prepareNewRule,
                        name -> ruleService.isRuleNameAvailable(name, null))
                .ifPresent(input -> {
                    Rule created = ruleService.createRule(input.name(), input.conditions(), input.actions(),
                            input.matchMode());
                    reloadRulesAndSelect(created.getId());
                });
    }

    private void mergeSelectedRules() {
        List<Rule> selectedRules = new ArrayList<>(ruleTable.getSelectionModel().getSelectedItems());
        if (selectedRules.isEmpty()) return;
        selectedRules.sort(Comparator.comparingInt(rule -> ruleTable.getItems().indexOf(rule)));
        if (selectedRules.stream().anyMatch(this::hasLegacyNoteAction)) {
            showMergeUnavailable("rules.merge.error.legacyNoteSource");
            return;
        }

        Set<Long> selectedIds = selectedRules.stream().map(Rule::getId).collect(Collectors.toSet());
        List<Rule> mergeTargets = ruleData.stream()
                .filter(rule -> !selectedIds.contains(rule.getId())).toList();
        if (selectedRules.size() == 1 && mergeTargets.isEmpty()) {
            showMergeUnavailable("rules.merge.error.noCompatibleTarget");
            return;
        }
        RuleMergeDialog.show(rootPane.getScene().getWindow(), selectedRules, mergeTargets)
                .ifPresent(options -> {
                    Rule target = options.targetRuleId() == null ? null : mergeTargets.stream()
                            .filter(rule -> Objects.equals(rule.getId(), options.targetRuleId()))
                            .findFirst().orElse(null);
                    if (options.targetRuleId() != null && target == null) return;
                    if (target != null && hasLegacyNoteAction(target)) {
                        showMergeUnavailable("rules.merge.error.legacyNoteTarget");
                        return;
                    }
                    openMergedRuleEditor(selectedRules, target, options.deleteSourceRules());
                });
    }

    private boolean hasLegacyNoteAction(Rule rule) {
        return rule.getActions().stream().anyMatch(action -> action.getActionType() == ActionType.SET_NOTE);
    }

    private void showMergeUnavailable(String messageKey) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        FintraxUI.configureDialog(alert);
        alert.initOwner(rootPane.getScene().getWindow());
        alert.setTitle(I18n.get("rules.merge.error.title"));
        alert.setHeaderText(I18n.get("rules.merge.error.title"));
        alert.setContentText(I18n.get(messageKey));
        alert.showAndWait();
    }

    private void openMergedRuleEditor(List<Rule> sourceRules, Rule targetRule, boolean deleteSourceRules) {
        List<RuleCondition> conditions = sourceRules.stream()
                .flatMap(rule -> rule.getConditions().stream()).collect(Collectors.toCollection(ArrayList::new));
        List<RuleAction> actions = sourceRules.stream()
                .flatMap(rule -> rule.getActions().stream()).collect(Collectors.toCollection(ArrayList::new));
        if (targetRule != null) {
            conditions.addAll(0, targetRule.getConditions());
            actions.addAll(0, targetRule.getActions());
        }
        boolean creatingNewRule = targetRule == null;
        RuleEditorDialog.show(rootPane.getScene().getWindow(), viewLoader,
                        creatingNewRule ? "dialog.newRule.title" : "dialog.editRule.title",
                        creatingNewRule ? "dialog.newRule.header" : "dialog.editRule.header",
                        creatingNewRule ? "createRuleSave" : "editRuleSave",
                        creatingNewRule ? "createRuleCancel" : "editRuleCancel",
                        editor -> editor.setRule(creatingNewRule ? "" : targetRule.getName(),
                                creatingNewRule ? RuleMatchMode.ANY : targetRule.getMatchMode(),
                                conditions, actions),
                        name -> ruleService.isRuleNameAvailable(name,
                                creatingNewRule ? null : targetRule.getId()))
                .ifPresent(input -> {
                    Long mergedRuleId;
                    if (creatingNewRule) {
                        Rule created = ruleService.createRule(input.name(), input.conditions(), input.actions(),
                                input.matchMode());
                        moveCreatedRuleBeforeSources(created, sourceRules);
                        mergedRuleId = created.getId();
                    } else {
                        ruleService.updateRule(targetRule.getId(), input.name(), input.conditions(), input.actions(),
                                input.matchMode());
                        mergedRuleId = targetRule.getId();
                    }
                    if (deleteSourceRules) deleteMergedSourceRules(sourceRules);
                    reloadRulesAndSelect(mergedRuleId);
                });
    }

    private void moveCreatedRuleBeforeSources(Rule created, List<Rule> sourceRules) {
        int firstSourceIndex = sourceRules.stream()
                .mapToInt(sourceRule -> indexOfRule(sourceRule.getId()))
                .min().orElse(ruleData.size());
        List<Long> orderedRuleIds = ruleData.stream().map(Rule::getId)
                .collect(Collectors.toCollection(ArrayList::new));
        orderedRuleIds.add(firstSourceIndex, created.getId());
        ruleService.reorderRules(orderedRuleIds);
        ruleNumberColumn.setSortType(TableColumn.SortType.ASCENDING);
        ruleTable.getSortOrder().setAll(ruleNumberColumn);
    }

    private void deleteMergedSourceRules(List<Rule> sourceRules) {
        sourceRules.forEach(rule -> ruleService.deleteRule(rule.getId()));
        ruleService.reorderRules(ruleService.getAllRules().stream().map(Rule::getId).toList());
    }

    private void editRule(Rule rule) {
        RuleEditorDialog.show(rootPane.getScene().getWindow(), viewLoader,
                        "dialog.editRule.title", "dialog.editRule.header",
                        "editRuleSave", "editRuleCancel",
                        editor -> editor.setRule(rule.getName(), rule.getMatchMode(),
                                rule.getConditions(), rule.getActions()),
                        name -> ruleService.isRuleNameAvailable(name, rule.getId()))
                .ifPresent(input -> {
                    ruleService.updateRule(rule.getId(), input.name(), input.conditions(), input.actions(),
                            input.matchMode());
                    reloadRulesAndSelect(rule.getId());
                });
    }

    private void reloadRulesAndSelect(Long ruleId) {
        loadRules();
        ruleData.stream().filter(rule -> Objects.equals(rule.getId(), ruleId))
                .findFirst().ifPresent(this::selectRule);
    }

    private void selectRule(Rule rule) {
        Rule matchingRule = ruleData.stream()
                .filter(candidate -> Objects.equals(candidate.getId(), rule.getId()))
                .findFirst().orElse(null);
        if (matchingRule == null) return;
        MultipleSelectionModel<Rule> selectionModel = ruleTable.getSelectionModel();
        Rule selected = selectionModel.getSelectedItem();
        if (selectionModel.getSelectedItems().size() == 1
                && selected != null && Objects.equals(selected.getId(), matchingRule.getId())) {
            showSelectedRule(matchingRule);
        } else {
            selectionModel.clearSelection();
            selectionModel.select(matchingRule);
        }
    }

    private int indexOfRule(Long ruleId) {
        for (int index = 0; index < ruleData.size(); index++) {
            if (Objects.equals(ruleData.get(index).getId(), ruleId)) return index;
        }
        return -1;
    }

    private void toggleRule(Rule rule) {
        Long ruleId = rule.getId();
        ruleService.toggleRule(ruleId);
        reloadRulesAndSelect(ruleId);
    }

    private void moveRule(Rule rule, int offset) {
        int index = indexOfRule(rule.getId());
        int destination = index + offset;
        if (index < 0 || destination < 0 || destination >= ruleData.size()) return;

        List<Long> ids = ruleData.stream().map(Rule::getId).collect(Collectors.toCollection(ArrayList::new));
        Collections.swap(ids, index, destination);
        ruleService.reorderRules(ids);
        reloadRulesAndSelect(rule.getId());
    }

    @FXML
    private void onMoveUp() {
        Rule selected = ruleTable.getSelectionModel().getSelectedItem();
        if (selected != null) moveRule(selected, -1);
    }

    @FXML
    private void onMoveDown() {
        Rule selected = ruleTable.getSelectionModel().getSelectedItem();
        if (selected != null) moveRule(selected, 1);
    }

    @FXML
    private void onApplyAll() {
        new RuleReviewDialog(rootPane.getScene().getWindow(),
                new RuleReviewDialog.Services(ruleService, accountService, categoryService, labelService,
                        settingsService),
                ruleService::previewAllRules, false).show();
    }

    private void confirmDeleteRule(Rule rule) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.get("rules.delete.confirm"), ButtonType.OK, ButtonType.CANCEL);
        FintraxUI.configureDialog(alert);
        alert.initOwner(rootPane.getScene().getWindow());
        alert.showAndWait().ifPresent(button -> {
            if (button != ButtonType.OK) return;
            ruleService.deleteRule(rule.getId());
            if (Objects.equals(selectedRuleId, rule.getId())) {
                ruleTable.getSelectionModel().clearSelection();
            }
            loadRules();
        });
    }

    private static FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(literal);
        icon.setIconSize(size);
        return icon;
    }
}
