package org.fintrax.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
public class StoreConfiguration {
    @Bean
    Path storagePath(@Value("${fintrax.storage.path:~/.fintrax/data}") String configuredPath) {
        return StoragePathResolver.resolve(configuredPath);
    }

    @Bean(destroyMethod = "shutdown")
    StoreManager storeManager(Path storagePath) {
        return new StoreManager(storagePath);
    }
}
