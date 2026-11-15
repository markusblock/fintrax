package org.fintrax.service;

import org.fintrax.model.AppSetting;
import org.fintrax.store.FintraxProperties;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsServiceTest {
    private Path tempDir;
    private StoreManager storeManager;
    private FintraxProperties fintraxProperties;
    private SettingsService settings;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-settings-test");
        Path storagePath = tempDir.resolve("data");
        storeManager = new StoreManager(storagePath);
        fintraxProperties = new FintraxProperties(tempDir.resolve("fintrax.properties"),
                storagePath.toString(), tempDir.resolve("backups").toString());
        settings = new SettingsService(storeManager, fintraxProperties);
    }

    @AfterEach
    void tearDown() throws Exception {
        storeManager.shutdown();
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.delete(path);
            } catch (Exception ignored) {
            }
        });
    }

    @Test
    void savesAndReadsSupportedPreferences() {
        settings.saveLanguage("de");
        settings.saveTheme("dark");

        assertEquals("de", settings.getLanguage());
        assertEquals("dark", settings.getTheme());
    }

    @Test
    void savedPreferencesSurviveStoreRestart() {
        settings.saveLanguage("de");
        settings.saveTheme("dark");
        settings.saveSettingValue("transaction.columns", "purpose=false");
        storeManager.shutdown();

        storeManager = new StoreManager(tempDir.resolve("data"));
        settings = new SettingsService(storeManager, fintraxProperties);

        assertEquals("de", settings.getLanguage());
        assertEquals("dark", settings.getTheme());
        assertEquals("purpose=false", settings.getSettingValue("transaction.columns"));
    }

    @Test
    void invalidOrMissingPreferencesUseDefaults() {
        storeManager.getRoot().getSettings().put("language", new AppSetting("language", "fr"));
        storeManager.getRoot().getSettings().put("theme", new AppSetting("theme", "solarized"));

        assertEquals(SettingsService.DEFAULT_LANGUAGE, settings.getLanguage());
        assertEquals(SettingsService.DEFAULT_THEME, settings.getTheme());
    }
}
