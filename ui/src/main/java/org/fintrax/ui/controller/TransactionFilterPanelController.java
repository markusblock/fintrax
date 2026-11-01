package org.fintrax.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.fintrax.config.I18n;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Category;
import org.fintrax.service.AccountService;
import org.fintrax.service.CategoryService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Scope("prototype")
public class TransactionFilterPanelController {
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

    private final AccountService accountService;
    private final CategoryService categoryService;
    private Runnable onFiltersChanged = () -> { };
    private boolean updatingDateFilters;
    private boolean updatingFilters;

    public TransactionFilterPanelController(AccountService accountService, CategoryService categoryService) {
        this.accountService = accountService;
        this.categoryService = categoryService;
    }

    @FXML
    public void initialize() {
        setupDateFilters();
        loadFilterOptions();
        setupFilterListeners();
    }

    public void setOnFiltersChanged(Runnable onFiltersChanged) {
        this.onFiltersChanged = onFiltersChanged == null ? () -> { } : onFiltersChanged;
    }

    public Optional<TransactionFilterCriteria> getCriteria() {
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
        return Optional.of(new TransactionFilterCriteria(
                getSelectedAccountId(), getSelectedCategoryId(), fromDate, toDate,
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
                    preset, LocalDate.now(), locale);
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
            onFiltersChanged.run();
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
            if (!datePicker.getEditor().getStyleClass().contains("invalid-date")) {
                datePicker.getEditor().getStyleClass().add("invalid-date");
            }
        } else {
            datePicker.getEditor().getStyleClass().remove("invalid-date");
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
