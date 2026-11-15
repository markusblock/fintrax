package org.fintrax.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
@Slf4j
public class StoreConfiguration {
    @Bean
    Path storagePath(StorageProperties properties) {
        return StoragePathResolver.resolve(properties.path());
    }

    @Bean
    BackupConfiguration backupConfiguration(Path storagePath, Environment environment) {
        String configPath = environment.getProperty("fintrax.backup.config", "~/.fintrax/backup.properties");
        String defaultBackupPath = environment.getProperty("fintrax.backup.default-path", "~/.fintrax/backups");
        return new BackupConfiguration(storagePath, Path.of(configPath), Path.of(defaultBackupPath),
                environment.getProperty("fintrax.backup.path"));
    }

    @Bean(destroyMethod = "shutdown")
    StoreManager storeManager(Path storagePath, BackupConfiguration backupConfiguration) {
        if (!backupConfiguration.isAvailable()) {
            log.warn("Automatic backups are unavailable: {}",
                    backupConfiguration.startupWarning().orElse("unknown error"));
            return new StoreManager(storagePath);
        }
        try {
            return new StoreManager(storagePath, backupConfiguration.continuousBackupDirectory());
        } catch (StoreManager.ContinuousBackupStartException exception) {
            String warning = "Unable to start continuous backup: " + exception.getMessage();
            backupConfiguration.disableForSession(warning);
            log.warn("{}; starting active store without backup", warning, exception);
            return new StoreManager(storagePath);
        }
    }
}
