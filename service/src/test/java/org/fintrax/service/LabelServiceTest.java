package org.fintrax.service;

import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class LabelServiceTest {
    private Path tempDir;
    private StoreManager store;
    private ActivityLogger activityLogger;
    private LabelService labelService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-test");
        store = new StoreManager(tempDir);
        activityLogger = new ActivityLogger(store);
        labelService = new LabelService(store, activityLogger);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.shutdown();
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> {
            try { Files.delete(p); } catch (Exception e) {}
        });
    }

    @Test
    void testCreateLabel() {
        Label label = labelService.createLabel("Important", "#FF0000", "High priority");

        assertNotNull(label.getId());
        assertEquals("Important", label.getName());
        assertEquals("#FF0000", label.getColor());
    }

    @Test
    void testDuplicateNameRejected() {
        labelService.createLabel("Important", "#FF0000", null);

        assertThrows(IllegalArgumentException.class, () ->
                labelService.createLabel("important", "#00FF00", null));
    }

    @Test
    void testDeleteLabelRemovesFromTransactions() {
        Label label = labelService.createLabel("Tag", "#0000FF", null);

        Transaction tx = Transaction.builder()
                .id(1L).accountId(1L).originalPayee("Test")
                .amount(java.math.BigDecimal.ONE)
                .bookingDate(java.time.LocalDate.now())
                .valueDate(java.time.LocalDate.now())
                .checksum("c1")
                .build();
        tx.getLabelIds().add(label.getId());
        store.getRoot().getTransactions().add(tx);

        labelService.deleteLabel(label.getId());

        assertTrue(store.getRoot().getTransactions().get(0).getLabelIds().isEmpty());
    }
}
