package org.fintrax.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTestFailureScreenshotExtensionTest {
    @TempDir
    Path screenshotDirectory;

    @Test
    void savesPngAndRethrowsOriginalFailure() throws Throwable {
        BufferedImage testImage = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        UiTestFailureScreenshotExtension extension = new UiTestFailureScreenshotExtension(
                screenshotDirectory, () -> testImage);
        AssertionError failure = new AssertionError("test failure");

        AssertionError thrown = assertThrows(AssertionError.class, () -> extension.handleTestExecutionException(
                extensionContext("engine:junit-jupiter/class:SampleTest/method:failed UI test()"), failure));

        assertSame(failure, thrown);
        List<Path> screenshots;
        try (Stream<Path> files = Files.list(screenshotDirectory)) {
            screenshots = files.toList();
        }
        assertEquals(1, screenshots.size());
        assertTrue(screenshots.getFirst().getFileName().toString().endsWith(".png"));

        BufferedImage savedImage = ImageIO.read(screenshots.getFirst().toFile());
        assertNotNull(savedImage);
        assertEquals(2, savedImage.getWidth());
        assertEquals(3, savedImage.getHeight());
    }

    private static ExtensionContext extensionContext(String uniqueId) {
        return (ExtensionContext) Proxy.newProxyInstance(
                ExtensionContext.class.getClassLoader(),
                new Class<?>[]{ExtensionContext.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId") ? uniqueId : null);
    }
}
