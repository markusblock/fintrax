package org.fintrax.service.hibiscus;

import org.fintrax.fintx.BankingProtocol;
import org.fintrax.model.BankAccount;
import org.fintrax.model.Transaction;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

class HibiscusXmlImportTestSupport {

    BankingProtocol noDirectoryProtocol() {
        return new BankingProtocol() {
            @Override
            public List<Transaction> fetchTransactions(BankAccount account, String pin) {
                return List.of();
            }

            @Override
            public boolean validatePin(BankAccount account, String pin) {
                return true;
            }

            @Override
            public String getProtocolName() {
                return "Hibiscus import test protocol";
            }
        };
    }

    File loadXmlTestData(String resourcePath) {
        java.net.URL url = getClass().getResource(resourcePath);
        if (url == null) {
            throw new IllegalStateException("Test resource not found: " + resourcePath);
        }
        try {
            return Paths.get(url.toURI()).toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    void deleteTempFiles(Path tempDir) {
        if (tempDir == null) return;
        try {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (Exception ignored) {}
            });
            Files.deleteIfExists(tempDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
