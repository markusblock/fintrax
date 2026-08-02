package org.fintrax.service;

import org.fintrax.model.AppSetting;
import org.fintrax.store.StoreManager;

import java.util.Locale;

public class SettingsService {
    public static final String LANGUAGE_KEY = "language";
    public static final String THEME_KEY = "theme";
    public static final String DEFAULT_LANGUAGE = "en";
    public static final String DEFAULT_THEME = "light";

    private final StoreManager storeManager;

    public SettingsService(StoreManager storeManager) {
        this.storeManager = storeManager;
    }

    public String getLanguage() {
        String value = getValue(LANGUAGE_KEY);
        return isSupportedLanguage(value) ? value : DEFAULT_LANGUAGE;
    }

    public String getTheme() {
        String value = getValue(THEME_KEY);
        return isSupportedTheme(value) ? value : DEFAULT_THEME;
    }

    public void saveLanguage(String language) {
        if (!isSupportedLanguage(language)) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        save(LANGUAGE_KEY, language);
    }

    public void saveTheme(String theme) {
        if (!isSupportedTheme(theme)) {
            throw new IllegalArgumentException("Unsupported theme: " + theme);
        }
        save(THEME_KEY, theme);
    }

    public Locale getLocale() {
        return Locale.forLanguageTag(getLanguage());
    }

    private String getValue(String key) {
        AppSetting setting = storeManager.getRoot().getSettings().get(key);
        return setting == null ? null : setting.getValue();
    }

    private void save(String key, String value) {
        AppSetting setting = storeManager.getRoot().getSettings().computeIfAbsent(
                key, ignored -> AppSetting.builder().key(key).build());
        setting.setValue(value);
        storeManager.store(setting);
        storeManager.store(storeManager.getRoot().getSettings());
    }

    private boolean isSupportedLanguage(String language) {
        return DEFAULT_LANGUAGE.equals(language) || "de".equals(language);
    }

    private boolean isSupportedTheme(String theme) {
        return DEFAULT_THEME.equals(theme) || "dark".equals(theme);
    }
}
