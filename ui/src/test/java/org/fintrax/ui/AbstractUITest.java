package org.fintrax.ui;

import org.fintrax.fintx.FintxConfiguration;
import org.fintrax.service.ServiceConfiguration;
import org.fintrax.store.StoreConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testfx.framework.junit5.ApplicationTest;

import javafx.stage.Stage;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Comparator;

public abstract class AbstractUITest extends ApplicationTest {
    private static Path tempDir;
    protected static AnnotationConfigApplicationContext applicationContext;

    @BeforeAll
    static void beforeAll() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-ui-test");
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test-properties", Map.of("fintrax.storage.path", tempDir.toString())));
        applicationContext.register(StoreConfiguration.class, FintxConfiguration.class,
                ServiceConfiguration.class, UiModule.class);
        applicationContext.refresh();
    }

    @AfterAll
    static void afterAll() throws Exception {
        applicationContext.close();
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                        }
                    });
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
    }
}
