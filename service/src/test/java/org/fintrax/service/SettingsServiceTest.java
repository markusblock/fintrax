package org.fintrax.service;

import org.fintrax.model.AppSetting;
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
    private SettingsService settings;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-settings-test");
        storeManager = new StoreManager(tempDir);
        settings = new SettingsService(storeManager);
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
        storeManager.shutdown();

        storeManager = new StoreManager(tempDir);
        settings = new SettingsService(storeManager);

        assertEquals("de", settings.getLanguage());
        assertEquals("dark", settings.getTheme());
    }

    @Test
    void invalidOrMissingPreferencesUseDefaults() {
        storeManager.getRoot().getSettings().put("language", new AppSetting("language", "fr"));
        storeManager.getRoot().getSettings().put("theme", new AppSetting("theme", "solarized"));

        assertEquals(SettingsService.DEFAULT_LANGUAGE, settings.getLanguage());
        assertEquals(SettingsService.DEFAULT_THEME, settings.getTheme());
    }
}
