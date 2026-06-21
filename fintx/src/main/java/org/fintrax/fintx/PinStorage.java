package org.fintrax.fintx;

import lombok.extern.slf4j.Slf4j;

import java.security.KeyStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Base64;

@Slf4j
public class PinStorage {
    private static final String KEYSTORE_ALIAS = "fintrax_pins";
    private static final char[] KEYSTORE_PASSWORD = "fintrax_keystore".toCharArray();

    private final Path keystorePath;
    private KeyStore keyStore;

    public PinStorage(Path storageDir) {
        this.keystorePath = storageDir.resolve("pins.keystore");
        initializeKeyStore();
    }

    private void initializeKeyStore() {
        try {
            keyStore = KeyStore.getInstance("JCEKS");

            if (Files.exists(keystorePath)) {
                try (FileInputStream fis = new FileInputStream(keystorePath.toFile())) {
                    keyStore.load(fis, KEYSTORE_PASSWORD);
                }
                log.info("Loaded existing PIN keystore");
            } else {
                keyStore.load(null, KEYSTORE_PASSWORD);
                saveKeyStore();
                log.info("Created new PIN keystore");
            }
        } catch (Exception e) {
            log.error("Failed to initialize PIN keystore", e);
            throw new RuntimeException("Failed to initialize PIN storage", e);
        }
    }

    public void storePin(String accountId, String pin) {
        try {
            KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(
                    new javax.crypto.spec.SecretKeySpec(pin.getBytes(), "AES")
            );
            keyStore.setEntry(KEYSTORE_ALIAS + "_" + accountId, entry,
                    new KeyStore.PasswordProtection(KEYSTORE_PASSWORD));
            saveKeyStore();
            log.info("Stored PIN for account {}", accountId);
        } catch (Exception e) {
            log.error("Failed to store PIN for account {}", accountId, e);
            throw new RuntimeException("Failed to store PIN", e);
        }
    }

    public String retrievePin(String accountId) {
        try {
            KeyStore.PasswordProtection protection = new KeyStore.PasswordProtection(KEYSTORE_PASSWORD);
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(
                    KEYSTORE_ALIAS + "_" + accountId, protection);

            if (entry == null) {
                return null;
            }

            return new String(entry.getSecretKey().getEncoded());
        } catch (Exception e) {
            log.error("Failed to retrieve PIN for account {}", accountId, e);
            return null;
        }
    }

    public void deletePin(String accountId) {
        try {
            keyStore.deleteEntry(KEYSTORE_ALIAS + "_" + accountId);
            saveKeyStore();
            log.info("Deleted PIN for account {}", accountId);
        } catch (Exception e) {
            log.error("Failed to delete PIN for account {}", accountId, e);
        }
    }

    private void saveKeyStore() throws Exception {
        try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
            keyStore.store(fos, KEYSTORE_PASSWORD);
        }
    }
}
