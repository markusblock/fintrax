package org.fintrax.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.fintrax.fintx.BankingProtocol;
import org.fintrax.fintx.PinStorage;
import org.fintrax.service.AccountService;
import org.fintrax.service.ActivityLogger;
import org.fintrax.service.CategoryService;
import org.fintrax.service.hibiscus.HibiscusXmlImporter;
import org.fintrax.service.LabelService;
import org.fintrax.service.ResetService;
import org.fintrax.service.RuleEngine;
import org.fintrax.service.RuleService;
import org.fintrax.service.SettingsService;
import org.fintrax.service.SyncService;
import org.fintrax.service.TransactionService;
import org.fintrax.store.BackupConfiguration;
import org.fintrax.store.StoreManager;
import org.fintrax.ui.ViewLoader;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

class FintraxSpringBootstrapTest {
    @TempDir
    Path tempDir;

    @Test
    void startsNonWebContext() {
        try (ConfigurableApplicationContext context = startContext()) {
            assertNotNull(context);
            assertTrue(context.isActive());
            assertFalse(context instanceof WebServerApplicationContext);
            assertFalse(context.getEnvironment().containsProperty("server.port"));
        }
    }

    @Test
    void closeIsDeterministic() {
        ConfigurableApplicationContext context = startContext();
        StoreManager storeManager = context.getBean(StoreManager.class);

        context.close();

        assertFalse(context.isActive());
        assertTrue(storeManager.isShutdown());
    }

    @Test
    void registersCompleteApplicationGraph() {
        try (ConfigurableApplicationContext context = startContext()) {
            assertNotNull(context.getBean(StoreManager.class));
            assertNotNull(context.getBean(PinStorage.class));
            assertNotNull(context.getBean(BankingProtocol.class));
            assertNotNull(context.getBean(ActivityLogger.class));
            assertNotNull(context.getBean(AccountService.class));
            assertNotNull(context.getBean(TransactionService.class));
            assertNotNull(context.getBean(CategoryService.class));
            assertNotNull(context.getBean(LabelService.class));
            assertNotNull(context.getBean(RuleEngine.class));
            assertNotNull(context.getBean(RuleService.class));
            assertNotNull(context.getBean(SyncService.class));
            assertNotNull(context.getBean(HibiscusXmlImporter.class));
            assertNotNull(context.getBean(SettingsService.class));
            assertNotNull(context.getBean(ResetService.class));
            assertNotNull(context.getBean(ViewLoader.class));
        }
    }

    @Test
    void startsActiveStoreWhenBackupDestinationIsUnavailable() {
        Path storagePath = tempDir.resolve("unavailable-data");
        try (ConfigurableApplicationContext context = FintraxSpringBootstrap.start(
                "--spring.main.banner-mode=off",
                "--fintrax.storage.path=" + storagePath,
                "--fintrax.backup.path=" + storagePath.resolve("backups"),
                "--fintrax.backup.config=" + tempDir.resolve("unavailable-backup.properties"))) {
            assertFalse(context.getBean(BackupConfiguration.class).isAvailable());
            assertNotNull(context.getBean(StoreManager.class).getRoot());
        }
    }

    private ConfigurableApplicationContext startContext() {
        return FintraxSpringBootstrap.start(
                "--spring.main.banner-mode=off",
                "--fintrax.storage.path=" + tempDir.resolve("data"),
                "--fintrax.backup.path=" + tempDir.resolve("backups"),
                "--fintrax.backup.config=" + tempDir.resolve("backup.properties"));
    }

    @Test
    void startupFailureIsPropagated() {
        assertThrows(RuntimeException.class, () -> FintraxSpringBootstrap.start(
                FailingConfiguration.class, "--spring.main.banner-mode=off"));
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingConfiguration {
        FailingConfiguration() {
            throw new IllegalStateException("startup failure");
        }
    }
}
