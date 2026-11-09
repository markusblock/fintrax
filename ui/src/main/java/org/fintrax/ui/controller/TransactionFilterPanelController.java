package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Category;
import org.fintrax.model.Label;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.LabelService;
import org.fintrax.service.TransactionFilter;
import org.fintrax.ui.control.LabelChipFactory;
import org.fintrax.ui.control.NaturalOrder;
import org.fintrax.ui.control.SearchableDropdownPicker;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Scope("prototype")
public class TransactionFilterPanelController {
    private static final String INVALID_DATE_STYLE_CLASS = "invalid-date";
    private static final String FILTER_SUMMARY_SEPARATOR = " · ";

    @FXML
    private ComboBox<String> accountFilter;
    @FXML
    private ComboBox<String> categoryFilter;
    @FXML
    private Button labelFilterButton;
    @FXML
    private TextField searchField;
    @FXML
    private TextField minAmountField;
    @FXML
    private TextField maxAmountField;
    @FXML
    private ComboBox<TransactionDateFilters.Preset> datePresetFilter;
    @FXML
    private DatePicker fromDateFilter;
    @FXML
    private DatePicker toDateFilter;
    @FXML
    private HBox collapsedFilterSummaryRow;
    @FXML
    private Button toggleAdditionalFiltersButton;
    @FXML
    private FontIcon toggleAdditionalFiltersIcon;
    @FXML
    private Tooltip toggleAdditionalFiltersTooltip;
    @FXML
    private TextField collapsedFilterSummary;
    @FXML
    private GridPane additionalFilters;

    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final SessionViewState sessionViewState;
    private List<Category> categoryOptions = List.of();
    private List<Label> labelOptions = List.of();
    private final Set<Long> selectedLabelIds = new LinkedHashSet<>();
    private Runnable onFiltersChanged = () -> { };
    private boolean updatingDateFilters;
    private boolean updatingFilters;
    private boolean collapsible;
    private boolean additionalFiltersExpanded = true;
    private String viewKey;

    public TransactionFilterPanelController(AccountService accountService, CategoryService categoryService,
                                            LabelService labelService, SessionViewState sessionViewState) {
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.sessionViewState = sessionViewState;
    }

    @FXML
    public void initialize() {
        setupDateFilters();
        loadFilterOptions();
        updateLabelFilterButton();
        setupFilterListeners();
        updateCollapsedFilterSummary();
    }

    @FXML
    private void onToggleAdditionalFilters() {
        if (!collapsible) return;
        additionalFiltersExpanded = !additionalFiltersExpanded;
        updateAdditionalFiltersVisibility();
        saveSessionState();
    }

    public void setOnFiltersChanged(Runnable onFiltersChanged) {
        this.onFiltersChanged = onFiltersChanged == null ? () -> { } : onFiltersChanged;
    }

    public void setCollapsible(boolean collapsible) {
        this.collapsible = collapsible;
        additionalFiltersExpanded = !collapsible;
        updateAdditionalFiltersVisibility();
    }

    public void restoreForView(String viewKey) {
        restoreForView(viewKey, TransactionDateFilters.Preset.THIS_YEAR);
    }

    void restoreForView(String viewKey, TransactionDateFilters.Preset defaultDatePreset) {
        this.viewKey = viewKey;
        SessionViewState.TransactionFilterPanelState state = sessionViewState.transactionFilters(viewKey);
        if (state == null) {
            datePresetFilter.setValue(defaultDatePreset);
            return;
        }

        updatingFilters = true;
        try {
            selectAccount(state.accountId());
            selectCategory(state.categoryId());
            selectedLabelIds.clear();
            if (state.labelIds() != null) selectedLabelIds.addAll(state.labelIds());
            searchField.setText(state.searchText());
            minAmountField.setText(state.minAmountText());
            maxAmountField.setText(state.maxAmountText());
            datePresetFilter.setValue(state.datePreset());
            updatingDateFilters = true;
            restoreDate(fromDateFilter, state.fromDate(), state.invalidFromDateText());
            restoreDate(toDateFilter, state.toDate(), state.invalidToDateText());
            additionalFiltersExpanded = state.additionalFiltersExpanded();
        } finally {
            updatingDateFilters = false;
            updatingFilters = false;
        }
        updateLabelFilterButton();
        updateAdditionalFiltersVisibility();
    }

    public Optional<TransactionFilter> getCriteria() {
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = TransactionDateFilters.parseDate(fromDateFilter.getEditor().getText(), I18n.getCurrentLocale());
        } catch (DateTimeParseException e) {
            setDateFieldInvalid(fromDateFilter, true);
            return Optional.empty();
        }
        try {
            toDate = TransactionDateFilters.parseDate(toDateFilter.getEditor().getText(), I18n.getCurrentLocale());
        } catch (DateTimeParseException e) {
            setDateFieldInvalid(toDateFilter, true);
            return Optional.empty();
        }

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            setDateFieldInvalid(fromDateFilter, true);
            setDateFieldInvalid(toDateFilter, true);
            return Optional.empty();
        }
        setDateFieldInvalid(fromDateFilter, false);
        setDateFieldInvalid(toDateFilter, false);

        String search = searchField.getText().trim();
        return Optional.of(new TransactionFilter(
                getSelectedAccountId(), fromDate, toDate, getSelectedCategoryId(),
                selectedLabelIds.isEmpty() ? null : Set.copyOf(selectedLabelIds),
                parseAmount(minAmountField.getText()), parseAmount(maxAmountField.getText()),
                search.isEmpty() ? null : search));
    }

    @FXML
    private void onOpenLabelFilter() {
        labelOptions = labelService.getAllLabels();
        Set<Long> availableIds = labelOptions.stream()
                .map(Label::getId)
                .collect(Collectors.toSet());
        if (selectedLabelIds.retainAll(availableIds)) applyChangedFilters();
        updateLabelFilterButton();
        SearchableDropdownPicker.showMany(labelFilterButton, labelOptions, selectedLabels(), Label::getName,
                "picker.labels.create", name -> labelService.createLabel(name, "#1976d2", null), labels -> {
                    selectedLabelIds.clear();
                    labels.stream().map(Label::getId).forEach(selectedLabelIds::add);
                    labelOptions = labelService.getAllLabels();
                    updateLabelFilterButton();
                    applyChangedFilters();
                });
    }

    @FXML
    private void onClearFilters() {
        updatingFilters = true;
        try {
            accountFilter.getSelectionModel().selectFirst();
            categoryFilter.getSelectionModel().selectFirst();
            selectedLabelIds.clear();
            updateLabelFilterButton();
            searchField.clear();
            minAmountField.clear();
            maxAmountField.clear();
            datePresetFilter.setValue(TransactionDateFilters.Preset.ALL_TIME);
            updatingDateFilters = true;
            fromDateFilter.setValue(null);
            toDateFilter.setValue(null);
            fromDateFilter.getEditor().clear();
            toDateFilter.getEditor().clear();
            setDateFieldInvalid(fromDateFilter, false);
            setDateFieldInvalid(toDateFilter, false);
        } finally {
            updatingDateFilters = false;
            updatingFilters = false;
        }
        updateCollapsedFilterSummary();
        saveSessionState();
        onFiltersChanged.run();
    }

    private void loadFilterOptions() {
        accountFilter.getItems().clear();
        accountFilter.getItems().add(I18n.get("filter.allAccounts"));
        for (BankAccount account : accountService.getAllAccounts()) {
            String display = (account.getBankName() != null ? account.getBankName() : "")
                    + " (" + maskIban(account.getIban()) + ")";
            accountFilter.getItems().add(display);
        }
        accountFilter.getSelectionModel().selectFirst();

        categoryFilter.getItems().clear();
        categoryFilter.getItems().add(I18n.get("filter.allCategories"));
        Collator collator = Collator.getInstance(I18n.getCurrentLocale());
        categoryOptions = categoryService.getAllCategories().stream()
                .sorted(Comparator.comparing(category -> category.getName() == null ? "" : category.getName(),
                        collator))
                .toList();
        for (Category category : categoryOptions) {
            categoryFilter.getItems().add(category.getName());
        }
        categoryFilter.getSelectionModel().selectFirst();
        labelOptions = labelService.getAllLabels();
    }

    private void setupDateFilters() {
        Locale locale = I18n.getCurrentLocale();
        DateTimeFormatter dateFormatter = TransactionDateFilters.formatter(locale);
        datePresetFilter.getItems().setAll(TransactionDateFilters.Preset.values());
        datePresetFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(TransactionDateFilters.Preset preset) {
                return preset == null ? "" : I18n.get("filter.datePreset." + preset.name());
            }

            @Override
            public TransactionDateFilters.Preset fromString(String text) {
                return Arrays.stream(TransactionDateFilters.Preset.values())
                        .filter(preset -> toString(preset).equals(text))
                        .findFirst()
                        .orElse(null);
            }
        });

        StringConverter<LocalDate> dateConverter = new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : dateFormatter.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                return TransactionDateFilters.parseDate(text, locale);
            }
        };
        fromDateFilter.setConverter(dateConverter);
        toDateFilter.setConverter(dateConverter);
        datePresetFilter.setValue(TransactionDateFilters.Preset.ALL_TIME);

        datePresetFilter.valueProperty().addListener((observable, oldPreset, preset) -> {
            if (preset == null || preset == TransactionDateFilters.Preset.CUSTOM) return;
            TransactionDateFilters.Range range = TransactionDateFilters.rangeFor(
                    preset, LocalDate.now(ZoneId.systemDefault()));
            updatingDateFilters = true;
            fromDateFilter.setValue(range.fromDate());
            toDateFilter.setValue(range.toDate());
            updatingDateFilters = false;
            setDateFieldInvalid(fromDateFilter, false);
            setDateFieldInvalid(toDateFilter, false);
        });

        fromDateFilter.getEditor().textProperty().addListener((observable, oldText, newText) ->
                onDateTextChanged(fromDateFilter));
        toDateFilter.getEditor().textProperty().addListener((observable, oldText, newText) ->
                onDateTextChanged(toDateFilter));
    }

    private void setupFilterListeners() {
        accountFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyChangedFilters());
        categoryFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyChangedFilters());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyChangedFilters());
        minAmountField.textProperty().addListener((observable, oldValue, newValue) -> applyChangedFilters());
        maxAmountField.textProperty().addListener((observable, oldValue, newValue) -> applyChangedFilters());
        datePresetFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyChangedFilters());
        fromDateFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyChangedFilters());
        toDateFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyChangedFilters());
        fromDateFilter.getEditor().textProperty().addListener(
                (observable, oldValue, newValue) -> applyChangedFilters());
        toDateFilter.getEditor().textProperty().addListener(
                (observable, oldValue, newValue) -> applyChangedFilters());
    }

    private void applyChangedFilters() {
        if (!updatingFilters && !updatingDateFilters) {
            updateCollapsedFilterSummary();
            saveSessionState();
            onFiltersChanged.run();
        }
    }

    private void saveSessionState() {
        if (viewKey == null) return;
        DateValue fromDate = captureDate(fromDateFilter);
        DateValue toDate = captureDate(toDateFilter);
        sessionViewState.saveTransactionFilters(viewKey, new SessionViewState.TransactionFilterPanelState(
                getSelectedAccountId(), getSelectedCategoryId(), Set.copyOf(selectedLabelIds), searchField.getText(),
                minAmountField.getText(), maxAmountField.getText(), datePresetFilter.getValue(),
                fromDate.value(), fromDate.invalidText(), toDate.value(), toDate.invalidText(),
                additionalFiltersExpanded));
    }

    private DateValue captureDate(DatePicker datePicker) {
        String text = datePicker.getEditor().getText();
        try {
            return new DateValue(TransactionDateFilters.parseDate(text, I18n.getCurrentLocale()), null);
        } catch (DateTimeParseException e) {
            return new DateValue(datePicker.getValue(), text);
        }
    }

    private void restoreDate(DatePicker datePicker, LocalDate value, String invalidText) {
        datePicker.setValue(value);
        if (invalidText != null) datePicker.getEditor().setText(invalidText);
    }

    private void selectAccount(Long accountId) {
        int index = 0;
        if (accountId != null) {
            List<BankAccount> accounts = accountService.getAllAccounts();
            for (int accountIndex = 0; accountIndex < accounts.size(); accountIndex++) {
                if (accountId.equals(accounts.get(accountIndex).getId())) {
                    index = accountIndex + 1;
                    break;
                }
            }
        }
        accountFilter.getSelectionModel().select(index);
    }

    private void selectCategory(Long categoryId) {
        int index = 0;
        if (categoryId != null) {
            for (int categoryIndex = 0; categoryIndex < categoryOptions.size(); categoryIndex++) {
                if (categoryId.equals(categoryOptions.get(categoryIndex).getId())) {
                    index = categoryIndex + 1;
                    break;
                }
            }
        }
        categoryFilter.getSelectionModel().select(index);
    }

    private void updateAdditionalFiltersVisibility() {
        boolean expanded = !collapsible || additionalFiltersExpanded;
        additionalFilters.setVisible(expanded);
        additionalFilters.setManaged(expanded);
        toggleAdditionalFiltersButton.setVisible(collapsible);
        toggleAdditionalFiltersButton.setManaged(collapsible);

        String toggleText = I18n.get(expanded
                ? "button.hideAdditionalFilters"
                : "button.showAdditionalFilters");
        toggleAdditionalFiltersButton.setAccessibleText(toggleText);
        toggleAdditionalFiltersTooltip.setText(toggleText);
        toggleAdditionalFiltersIcon.setIconLiteral(expanded ? "fth-chevron-up" : "fth-chevron-down");
        updateCollapsedFilterSummary();
    }

    private void updateCollapsedFilterSummary() {
        List<String> activeFilters = new ArrayList<>();
        if (accountFilter.getSelectionModel().getSelectedIndex() > 0) {
            addSummaryItem(activeFilters, I18n.get("filter.account"), accountFilter.getValue());
        }
        if (categoryFilter.getSelectionModel().getSelectedIndex() > 0) {
            addSummaryItem(activeFilters, I18n.get("filter.category"), categoryFilter.getValue());
        }
        List<String> selectedLabelNames = selectedLabels().stream().map(Label::getName).toList();
        if (!selectedLabelNames.isEmpty()) {
            addSummaryItem(activeFilters, I18n.get("filter.labels"), String.join(", ", selectedLabelNames));
        }
        addSummaryItem(activeFilters, I18n.get("filter.minAmount"), minAmountField.getText());
        addSummaryItem(activeFilters, I18n.get("filter.maxAmount"), maxAmountField.getText());

        String summary = String.join(FILTER_SUMMARY_SEPARATOR, activeFilters);
        collapsedFilterSummary.setText(summary);
        boolean showSummary = collapsible && !additionalFiltersExpanded && !summary.isEmpty();
        collapsedFilterSummaryRow.setVisible(showSummary);
        collapsedFilterSummaryRow.setManaged(showSummary);
        collapsedFilterSummary.setVisible(showSummary);
        collapsedFilterSummary.setManaged(showSummary);
    }

    private void addSummaryItem(List<String> activeFilters, String label, String value) {
        if (value != null && !value.isBlank()) {
            activeFilters.add(I18n.get("filter.summary.item", label, value.trim()));
        }
    }

    private void onDateTextChanged(DatePicker datePicker) {
        setDateFieldInvalid(fromDateFilter, false);
        setDateFieldInvalid(toDateFilter, false);
        if (!updatingDateFilters && !matchesSelectedDatePreset(datePicker)) {
            datePresetFilter.setValue(TransactionDateFilters.Preset.CUSTOM);
        }
    }

    private boolean matchesSelectedDatePreset(DatePicker datePicker) {
        TransactionDateFilters.Preset preset = datePresetFilter.getValue();
        if (preset == null || preset == TransactionDateFilters.Preset.CUSTOM) return false;
        TransactionDateFilters.Range range = TransactionDateFilters.rangeFor(
                preset, LocalDate.now(ZoneId.systemDefault()));
        LocalDate expectedDate = datePicker == fromDateFilter ? range.fromDate() : range.toDate();
        try {
            LocalDate enteredDate = TransactionDateFilters.parseDate(
                    datePicker.getEditor().getText(), I18n.getCurrentLocale());
            return Objects.equals(expectedDate, enteredDate);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private void setDateFieldInvalid(DatePicker datePicker, boolean invalid) {
        if (invalid) {
            if (!datePicker.getEditor().getStyleClass().contains(INVALID_DATE_STYLE_CLASS)) {
                datePicker.getEditor().getStyleClass().add(INVALID_DATE_STYLE_CLASS);
            }
        } else {
            datePicker.getEditor().getStyleClass().remove(INVALID_DATE_STYLE_CLASS);
        }
    }

    private Long getSelectedAccountId() {
        int index = accountFilter.getSelectionModel().getSelectedIndex();
        if (index <= 0) return null;
        List<BankAccount> accounts = accountService.getAllAccounts();
        return index - 1 < accounts.size() ? accounts.get(index - 1).getId() : null;
    }

    private Long getSelectedCategoryId() {
        int index = categoryFilter.getSelectionModel().getSelectedIndex();
        if (index <= 0) return null;
        return index - 1 < categoryOptions.size() ? categoryOptions.get(index - 1).getId() : null;
    }

    private List<Label> selectedLabels() {
        Comparator<String> labelOrder = NaturalOrder.comparator(I18n.getCurrentLocale());
        return labelOptions.stream()
                .filter(label -> selectedLabelIds.contains(label.getId()))
                .sorted(Comparator.comparing(Label::getName, labelOrder))
                .toList();
    }

    private void updateLabelFilterButton() {
        List<Label> labels = selectedLabels();
        HBox content = new HBox(6);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.Node selectedContent;
        if (labels.isEmpty()) {
            selectedContent = new javafx.scene.control.Label(I18n.get("filter.labels"));
        } else {
            selectedContent = LabelChipFactory.createAll(labels);
            HBox.setHgrow(selectedContent, Priority.ALWAYS);
            ((Region) selectedContent).setMaxWidth(Double.MAX_VALUE);
        }
        content.getChildren().setAll(selectedContent, new javafx.scene.control.Label("▾"));
        labelFilterButton.setText("");
        labelFilterButton.setGraphic(content);
        String selectedLabelNames = labels.stream().map(Label::getName).collect(Collectors.joining(", "));
        labelFilterButton.setAccessibleText(labels.isEmpty()
                ? I18n.get("filter.labels")
                : I18n.get("filter.labels") + ": " + selectedLabelNames);
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return new BigDecimal(text.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String maskIban(String iban) {
        if (iban == null || iban.length() < 8) return iban != null ? iban : "";
        return iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
    }

    private record DateValue(LocalDate value, String invalidText) {
    }
}
