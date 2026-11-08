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
    private TableView<Transaction> ruleTransactionsTable;
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
        TableViewResizePolicy.fitColumnsAndAllowHorizontalScrolling(ruleTable);
        TableCellTooltips.install(ruleTable);
        ruleTable.setRowFactory(table -> new TableRow<>() {
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
        });
        loadRules();
        setupTransactionTable();
        TableCellTooltips.install(ruleTransactionsTable);
        ruleTransactionsTable.setItems(displayedTransactions);
        setupColumnMenu();
        displayedTransactions.addListener((javafx.collections.ListChangeListener<Transaction>) change ->
                updateTransactionTotal());
        updateTransactionTotal();
        transactionTableActions.installContextMenu(
                ruleTransactionsTable, rootPane, this::reloadSelectedTransactionsKeepingSelection, null);
        transactionFilterPanelController.setCollapsible(true);
        transactionFilterPanelController.restoreForView(SessionViewState.RULES_VIEW);
        transactionFilterPanelController.setOnFiltersChanged(this::refreshSelectedRuleTransactions);
        ruleTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRule, newRule) ->
                showSelectedRule(newRule));
        restoreSelectedRule();
    }

    private void setupColumnMenu() {
        transactionTableSetup.installColumnMenu(COLUMN_CONFIG_KEY, settingsService);
    }

    private void setupTransactionTable() {
        transactionTableSetup = new TransactionTableSetup(ruleTransactionsTable,
                accountService, categoryService, labelService);
        transactionTableSetup.configure();
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

        return new ContextMenu(editRule, applyRule, new SeparatorMenuItem(), moveUp, moveDown,
                toggleEnabled, new SeparatorMenuItem(), deleteRule);
    }

    private void loadRules() {
        ruleData.setAll(ruleService.getAllRules());
    }

    private void showSelectedRule(Rule rule) {
        if (rule == null) {
            clearSelectedRule();
            return;
        }
        selectedRuleId = rule.getId();
        sessionViewState.setSelectedRuleId(selectedRuleId);
        ruleNameSummary.setText(rule.getName());
        ruleConditionsSummary.setText(I18n.get("rules.summary.conditions", rule.getConditions().stream()
                .map(this::conditionSummary).collect(Collectors.joining(" · "))));
        ruleActionsSummary.setText(I18n.get("rules.summary.actions", rule.getActions().stream()
                .map(this::actionSummary)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" · "))));
        selectedRuleTransactions = ruleService.getMatchingTransactions(rule.getId());
        refreshSelectedRuleTransactions();
    }

    private void clearSelectedRule() {
        selectedRuleId = null;
        sessionViewState.setSelectedRuleId(null);
        selectedRuleTransactions = List.of();
        ruleNameSummary.setText(I18n.get("rules.selectRule"));
        ruleConditionsSummary.setText("");
        ruleActionsSummary.setText("");
        displayedTransactions.clear();
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
        return condition.getMatchField().name() + " " + condition.getOperator().name() + " " + value;
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
            selectedRuleTransactions = ruleService.getMatchingTransactions(selectedRuleId);
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
                        "createRuleSave", "createRuleCancel", RuleEditorController::prepareNewRule)
                .ifPresent(input -> {
                    Rule created = ruleService.createRule(input.name(), input.conditions(), input.actions());
                    reloadRulesAndSelect(created.getId());
                });
    }

    private void editRule(Rule rule) {
        RuleEditorDialog.show(rootPane.getScene().getWindow(), viewLoader,
                        "dialog.editRule.title", "dialog.editRule.header",
                        "editRuleSave", "editRuleCancel",
                        editor -> editor.setRule(rule.getName(), rule.getConditions(), rule.getActions()))
                .ifPresent(input -> {
                    ruleService.updateRule(rule.getId(), input.name(), input.conditions(), input.actions());
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
        Rule selected = ruleTable.getSelectionModel().getSelectedItem();
        if (selected != null && Objects.equals(selected.getId(), matchingRule.getId())) {
            showSelectedRule(matchingRule);
        } else {
            ruleTable.getSelectionModel().select(matchingRule);
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
