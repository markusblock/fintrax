package org.fintrax.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.context.WebServerApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FintraxSpringBootstrapTest {
    @Test
    void startsNonWebContext() {
        try (ConfigurableApplicationContext context = FintraxSpringBootstrap.start(
                "--spring.main.banner-mode=off")) {
            assertNotNull(context);
            assertTrue(context.isActive());
            assertFalse(context instanceof WebServerApplicationContext);
            assertFalse(context.getEnvironment().containsProperty("server.port"));
        }
    }

    @Test
    void closeIsDeterministic() {
        ConfigurableApplicationContext context = FintraxSpringBootstrap.start(
                "--spring.main.banner-mode=off");

        context.close();

        assertFalse(context.isActive());
    }

    @Test
    void startupFailureIsPropagated() {
        assertThrows(RuntimeException.class, () -> new SpringApplicationBuilder(FailingConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off"));
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingConfiguration {
        FailingConfiguration() {
            throw new IllegalStateException("startup failure");
        }
    }
}
