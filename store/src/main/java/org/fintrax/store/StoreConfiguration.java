package org.fintrax.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class StoreConfiguration {
    @Bean
    FintraxProperties fintraxProperties(Environment environment) {
        String configurationFile = environment.getProperty("fintrax.config.path", FintraxProperties.DEFAULT_FILE);
        return new FintraxProperties(Path.of(configurationFile),
                environment.getProperty("fintrax.storage.path"), environment.getProperty("fintrax.backup.path"));
    }

    @Bean
    Path storagePath(FintraxProperties properties) {
        return StoragePathResolver.resolve(properties.storagePath().toString());
    }

    @Bean(destroyMethod = "shutdown")
    StoreManager storeManager(Path storagePath, FintraxProperties fintraxProperties) {
        if (!fintraxProperties.isAvailable()) {
            log.warn("Automatic backups are unavailable: {}",
                    fintraxProperties.startupWarning().orElse("unknown error"));
            return new StoreManager(storagePath);
        }
        try {
            return new StoreManager(storagePath, fintraxProperties.continuousBackupDirectory());
        } catch (StoreManager.ContinuousBackupStartException exception) {
            String warning = "Unable to start continuous backup: " + exception.getMessage();
            fintraxProperties.disableForSession(warning);
            log.warn("{}; starting active store without backup", warning, exception);
            return new StoreManager(storagePath);
        }
    }
}
