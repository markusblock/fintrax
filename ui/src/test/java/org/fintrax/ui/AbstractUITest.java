package org.fintrax.ui;

import org.fintrax.service.ServiceRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testfx.framework.junit5.ApplicationTest;

import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public abstract class AbstractUITest extends ApplicationTest {
    private static Path tempDir;
    protected static AnnotationConfigApplicationContext applicationContext;

    @BeforeAll
    static void beforeAll() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-ui-test");
        System.setProperty("fintrax.storage.path", tempDir.toString());
        ServiceRegistry.initialize();
        applicationContext = new AnnotationConfigApplicationContext(UiModule.class);
    }

    @AfterAll
    static void afterAll() throws Exception {
        ServiceRegistry.shutdown();
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
