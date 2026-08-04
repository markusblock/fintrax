package org.fintrax.fintx;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
public class FintxConfiguration {
    @Bean
    BankingProtocol bankingProtocol() {
        return new FinTsAdapter();
    }

    @Bean
    PinStorage pinStorage(@Qualifier("storagePath") Path storagePath) {
        return new PinStorage(storagePath);
    }
}
