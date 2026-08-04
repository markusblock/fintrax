package org.fintrax.store;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fintrax.storage")
public record StorageProperties(String path) {
    public StorageProperties {
        if (path == null || path.isBlank()) {
            path = "~/.fintrax/data";
        }
    }
}
