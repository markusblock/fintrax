package org.fintrax.app;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts the embedded Spring context without taking ownership of the desktop lifecycle. */
@SpringBootConfiguration
public class FintraxSpringBootstrap {
    private FintraxSpringBootstrap() {
    }

    public static ConfigurableApplicationContext start(String... args) {
        return start(FintraxSpringBootstrap.class, args);
    }

    static ConfigurableApplicationContext start(Class<?> source, String... args) {
        return new SpringApplicationBuilder(source)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
