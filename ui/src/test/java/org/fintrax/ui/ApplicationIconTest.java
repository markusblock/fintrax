package org.fintrax.ui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

}
