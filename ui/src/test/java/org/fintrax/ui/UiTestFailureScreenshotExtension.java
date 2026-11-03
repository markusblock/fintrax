package org.fintrax.ui;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;

public final class UiTestFailureScreenshotExtension implements TestExecutionExceptionHandler {
    private static final String SCREENSHOT_DIRECTORY_ENV = "FINTRAX_UI_TEST_FAILURE_SCREENSHOT_DIR";

    private final Path screenshotDirectory;
    private final Supplier<BufferedImage> screenCapture;

    public UiTestFailureScreenshotExtension() {
        this(screenshotDirectoryFromEnvironment(), UiTestFailureScreenshotExtension::captureScreen);
    }

    UiTestFailureScreenshotExtension(Path screenshotDirectory, Supplier<BufferedImage> screenCapture) {
        this.screenshotDirectory = screenshotDirectory;
        this.screenCapture = screenCapture;
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable testFailure) throws Throwable {
        if (screenshotDirectory != null) {
            try {
                saveScreenshot(context.getUniqueId());
            } catch (Exception screenshotFailure) {
                System.err.println("Could not save UI test failure screenshot: " + screenshotFailure.getMessage());
            }
        }

        throw testFailure;
    }

    private void saveScreenshot(String testId) throws IOException {
        Files.createDirectories(screenshotDirectory);
        String safeTestId = testId.replaceAll("[^A-Za-z0-9._-]", "_");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Path destination = screenshotDirectory.resolve(safeTestId + "-" + suffix + ".png");
        if (!ImageIO.write(screenCapture.get(), "png", destination.toFile())) {
            throw new IOException("No PNG writer is available");
        }
        System.err.println("Saved UI test failure screenshot: " + destination);
    }

    private static Path screenshotDirectoryFromEnvironment() {
        String configuredDirectory = System.getenv(SCREENSHOT_DIRECTORY_ENV);
        return configuredDirectory == null || configuredDirectory.isBlank()
                ? null
                : Path.of(configuredDirectory);
    }

    private static BufferedImage captureScreen() {
        try {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            return new Robot().createScreenCapture(new Rectangle(screenSize));
        } catch (AWTException exception) {
            throw new IllegalStateException("Could not capture the Xvfb display", exception);
        }
    }
}
