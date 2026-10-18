package org.fintrax.ui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationIconTest {
    private static final int[] ICON_SIZES = {16, 32, 48, 64, 128, 256};

    @Test
    void providesSquareIconImagesAtCommonDesktopScaleSizes() throws IOException {
        for (int size : ICON_SIZES) {
            String resourcePath = "/icons/fintrax-" + size + ".png";
            try (InputStream iconStream = getClass().getResourceAsStream(resourcePath)) {
                assertNotNull(iconStream, "Missing icon resource: " + resourcePath);
                var image = ImageIO.read(iconStream);

                assertNotNull(image, "Unreadable icon resource: " + resourcePath);
                assertEquals(size, image.getWidth(), "Unexpected width: " + resourcePath);
                assertEquals(size, image.getHeight(), "Unexpected height: " + resourcePath);
            }
        }
    }

    @Test
    void leavesTransparentSpaceOutsideTheWhiteTileInsteadOfDrawingAnOutline() throws IOException {
        try (InputStream iconStream = getClass().getResourceAsStream("/icons/fintrax-256.png")) {
            assertNotNull(iconStream, "Missing 256 px icon resource");
            var image = ImageIO.read(iconStream);

            assertNotNull(image, "Unreadable 256 px icon resource");
            assertEquals(0, image.getRGB(128, 4) >>> 24, "Expected no outline above the tile");
        }
    }

    @Test
    void keepsTheTileInsetFromTheCanvasForDockVisualBalance() throws IOException {
        try (InputStream iconStream = getClass().getResourceAsStream("/icons/fintrax-256.png")) {
            assertNotNull(iconStream, "Missing 256 px icon resource");
            var image = ImageIO.read(iconStream);

            assertNotNull(image, "Unreadable 256 px icon resource");
            assertEquals(0, image.getRGB(128, 20) >>> 24, "Expected transparent space around the tile");
        }
    }

    @Test
    void keepsTheTileWithinTenPercentOfTheCanvasEdge() throws IOException {
        try (InputStream iconStream = getClass().getResourceAsStream("/icons/fintrax-256.png")) {
            assertNotNull(iconStream, "Missing 256 px icon resource");
            var image = ImageIO.read(iconStream);

            assertNotNull(image, "Unreadable 256 px icon resource");
            assertTrue((image.getRGB(128, 26) >>> 24) > 0, "Expected the tile within ten percent of the edge");
        }
    }
}
