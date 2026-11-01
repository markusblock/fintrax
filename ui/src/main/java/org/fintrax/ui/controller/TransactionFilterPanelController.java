package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fintrax.config.I18n;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Category;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.fintrax.service.TransactionFilter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    private Runnable onFiltersChanged = () -> { };
    private boolean updatingDateFilters;
    private boolean updatingFilters;
    private boolean collapsible;
    private boolean additionalFiltersExpanded = true;

    public TransactionFilterPanelController(AccountService accountService, CategoryService categoryService) {
        this.accountService = accountService;
        this.categoryService = categoryService;
    }

    @FXML
    public void initialize() {
        setupDateFilters();
        loadFilterOptions();
        setupFilterListeners();
        updateCollapsedFilterSummary();
    }

    @FXML
    private void onToggleAdditionalFilters() {
        if (!collapsible) return;
        additionalFiltersExpanded = !additionalFiltersExpanded;
        updateAdditionalFiltersVisibility();
    }

    public void setOnFiltersChanged(Runnable onFiltersChanged) {
        this.onFiltersChanged = onFiltersChanged == null ? () -> { } : onFiltersChanged;
    }

    public void setCollapsible(boolean collapsible) {
        this.collapsible = collapsible;
        additionalFiltersExpanded = !collapsible;
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
                getSelectedAccountId(), fromDate, toDate, getSelectedCategoryId(), null,
                parseAmount(minAmountField.getText()), parseAmount(maxAmountField.getText()),
                search.isEmpty() ? null : search));
    }

    @FXML
    private void onClearFilters() {
        updatingFilters = true;
        try {
            accountFilter.getSelectionModel().selectFirst();
            categoryFilter.getSelectionModel().selectFirst();
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
        for (Category category : categoryService.getAllCategories()) {
            categoryFilter.getItems().add(category.getName());
        }
        categoryFilter.getSelectionModel().selectFirst();
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
                    preset, LocalDate.now(ZoneId.systemDefault()), locale);
            updatingDateFilters = true;
            fromDateFilter.setValue(range.fromDate());
            toDateFilter.setValue(range.toDate());
            updatingDateFilters = false;
            setDateFieldInvalid(fromDateFilter, false);
            setDateFieldInvalid(toDateFilter, false);
        });

        fromDateFilter.getEditor().textProperty().addListener((observable, oldText, newText) ->
                onDateTextChanged());
        toDateFilter.getEditor().textProperty().addListener((observable, oldText, newText) ->
                onDateTextChanged());
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
            onFiltersChanged.run();
        }
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

    private void onDateTextChanged() {
        setDateFieldInvalid(fromDateFilter, false);
        setDateFieldInvalid(toDateFilter, false);
        if (!updatingDateFilters) {
            datePresetFilter.setValue(TransactionDateFilters.Preset.CUSTOM);
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
        List<Category> categories = categoryService.getAllCategories();
        return index - 1 < categories.size() ? categories.get(index - 1).getId() : null;
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
}
