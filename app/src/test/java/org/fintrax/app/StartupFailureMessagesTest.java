package org.fintrax.app;

import org.fintrax.config.I18n;
import org.eclipse.store.storage.exceptions.StorageException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupFailureMessagesTest {
    @Test
    void storageInUseMessageNamesStorageAndExplainsHowToRecover() {
        I18n.setLocale(Locale.ENGLISH);

        String message = I18n.get("startup.storageInUse.message", Path.of("/home/alex/.fintrax/data"));

        assertAll(
                () -> assertTrue(message.contains("/home/alex/.fintrax/data")),
                () -> assertTrue(message.contains("Close the other instance")),
                () -> assertTrue(message.contains("try again"))
        );
    }

    @Test
    void storageInUseMessageCanUseGermanLocale() {
        I18n.setLocale(Locale.GERMAN);

        assertEquals("Fintrax bereits gestartet", I18n.get("startup.storageInUse.title"));

        String storagePath = "/home/alex/.fintrax/data";
        String message = I18n.get("startup.storageInUse.message", Path.of(storagePath));

        assertEquals("Fintrax ist bereits gestartet und kann nicht ein weiteres Mal gestartet werden.\n"
                + "Speicherort: " + storagePath + "\n"
                + "Schließen Sie die andere Instanz und versuchen Sie es erneut.", message);
    }

    @Test
    void storageErrorMessageIncludesPathAndRestoreOption() {
        I18n.setLocale(Locale.ENGLISH);

        String message = I18n.get("startup.storageError.message", Path.of("/home/alex/.fintrax/data"),
                "Unreadable transaction file");

        assertAll(
                () -> assertTrue(message.contains("/home/alex/.fintrax/data")),
                () -> assertTrue(message.contains("Unreadable transaction file")),
                () -> assertTrue(I18n.get("startup.storageError.restore").contains("Restore")),
                () -> assertTrue(I18n.get("startup.storageError.changeBackup").contains("Change"))
        );
    }

    @Test
    void findsStorageExceptionThroughSpringCauseChain() {
        StorageException storageException = new StorageException("Unreadable transaction file");

        assertSame(storageException, FintraxApplication.findStorageException(
                new RuntimeException("Spring startup failed", new RuntimeException(storageException))));
    }
}
