package org.fintrax.ui;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

final class ApplicationIcon {
    private static final int[] ICON_SIZES = {16, 32, 48, 64, 128, 256};
    private static final int DESKTOP_ICON_SIZE = 256;

    private ApplicationIcon() {}

    static void applyTo(Stage stage) {
        for (int size : ICON_SIZES) {
            stage.getIcons().add(loadImage(size));
        }

        applyToNativeTaskbar();
    }

    private static Image loadImage(int size) {
        URL resource = iconResource(size);
        if (resource == null) {
            throw new IllegalStateException("Missing Fintrax icon resource at size " + size);
        }
        return new Image(resource.toExternalForm());
    }

    private static URL iconResource(int size) {
        return ApplicationIcon.class.getResource("/icons/fintrax-" + size + ".png");
    }

    private static void applyToNativeTaskbar() {
        if (!Taskbar.isTaskbarSupported()) {
            return;
        }

        Taskbar taskbar = Taskbar.getTaskbar();
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            return;
        }

        try {
            BufferedImage image = ImageIO.read(iconResource(DESKTOP_ICON_SIZE));
            if (image == null) {
                throw new IllegalStateException("Unable to read Fintrax desktop icon");
            }
            taskbar.setIconImage(image);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load Fintrax desktop icon", e);
        }
    }
}
