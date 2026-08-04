package org.fintrax.store;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StoreConfiguration {
    @Bean
    Path storagePath(StorageProperties properties) {
        return StoragePathResolver.resolve(properties.path());
    }

    @Bean(destroyMethod = "shutdown")
    StoreManager storeManager(Path storagePath) {
        return new StoreManager(storagePath);
    }
}
