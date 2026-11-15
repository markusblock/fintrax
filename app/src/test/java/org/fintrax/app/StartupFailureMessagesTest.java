package org.fintrax.app;

import org.fintrax.config.I18n;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupFailureMessagesTest {
    @Test
    void storageInUseMessageNamesStorageAndExplainsHowToRecover() {
        I18n.setLocale(Locale.ENGLISH);

        String message = StartupFailureMessages.storageInUse(Path.of("/home/alex/.fintrax/data"));

        assertAll(
                () -> assertTrue(message.contains("/home/alex/.fintrax/data")),
                () -> assertTrue(message.contains("Close the other instance")),
                () -> assertTrue(message.contains("try again"))
        );
    }

    @Test
    void storageInUseMessageCanUseGermanLocale() {
        I18n.setLocale(Locale.GERMAN);

        String message = StartupFailureMessages.storageInUse(Path.of("/home/alex/.fintrax/data"));

        assertAll(
                () -> assertTrue(message.contains("/home/alex/.fintrax/data")),
                () -> assertTrue(message.contains("Schließen Sie die andere Instanz")),
                () -> assertTrue(message.contains("versuchen Sie es erneut"))
        );
    }
}
