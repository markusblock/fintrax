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

class CategoryServiceTest {
    private Path tempDir;
    private StoreManager store;
    private ActivityLogger activityLogger;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-test");
        store = new StoreManager(tempDir);
        activityLogger = new ActivityLogger(store);
        categoryService = new CategoryService(store, activityLogger);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.shutdown();
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> {
            try { Files.delete(p); } catch (Exception e) {}
        });
    }

    @Test
    void testCreateCategory() {
        Category cat = categoryService.createCategory("Food", null, "#FF0000", null);

        assertNotNull(cat.getId());
        assertEquals("Food", cat.getName());
        assertNull(cat.getParentId());
    }

    @Test
    void testCreateChildCategory() {
        Category parent = categoryService.createCategory("Food", null, null, null);
        Category child = categoryService.createCategory("Groceries", parent.getId(), null, null);

        assertEquals(parent.getId(), child.getParentId());
    }

    @Test
    void testMaxDepth() {
        Category c1 = categoryService.createCategory("L1", null, null, null);
        Category c2 = categoryService.createCategory("L2", c1.getId(), null, null);
        Category c3 = categoryService.createCategory("L3", c2.getId(), null, null);
        Category c4 = categoryService.createCategory("L4", c3.getId(), null, null);
        Category c5 = categoryService.createCategory("L5", c4.getId(), null, null);

        assertThrows(IllegalArgumentException.class, () ->
                categoryService.createCategory("L6", c5.getId(), null, null));
    }

    @Test
    void testGetRootCategories() {
        int initialRootCount = categoryService.getRootCategories().size();
        categoryService.createCategory("TestRoot1", null, null, null);
        Category root2 = categoryService.createCategory("TestRoot2", null, null, null);
        categoryService.createCategory("Child", root2.getId(), null, null);

        assertEquals(initialRootCount + 2, categoryService.getRootCategories().size());
    }

    @Test
    void testCreateDuplicateSiblingRejected() {
        categoryService.createCategory("Food", null, null, null);

        assertThrows(IllegalArgumentException.class, () ->
                categoryService.createCategory("Food", null, null, null));
    }

    @Test
    void testCreateSameNameUnderDifferentParentAllowed() {
        Category parent1 = categoryService.createCategory("Parent1", null, null, null);
        Category parent2 = categoryService.createCategory("Parent2", null, null, null);

        categoryService.createCategory("Shared", parent1.getId(), null, null);
        Category child2 = categoryService.createCategory("Shared", parent2.getId(), null, null);

        assertEquals(parent2.getId(), child2.getParentId());
    }

    @Test
    void testUpdateCategoryRenameToDuplicateSiblingRejected() {
        Category cat1 = categoryService.createCategory("Cat1", null, null, null);
        categoryService.createCategory("Cat2", null, null, null);

        assertThrows(IllegalArgumentException.class, () ->
                categoryService.updateCategory(cat1.getId(), "Cat2", null, null));
    }

    @Test
    void testUpdateCategoryRenameToNameUsedUnderAnotherParentAllowed() {
        Category parent1 = categoryService.createCategory("Parent1", null, null, null);
        Category parent2 = categoryService.createCategory("Parent2", null, null, null);
        Category cat = categoryService.createCategory("Old", parent1.getId(), null, null);
        categoryService.createCategory("New", parent2.getId(), null, null);

        categoryService.updateCategory(cat.getId(), "New", null, null);

        assertEquals("New", categoryService.getCategory(cat.getId()).get().getName());
    }

    @Test
    void testDeleteCategoryReassignsTransactions() {
        CategoryService txService = new CategoryService(store, activityLogger);
        Category cat1 = categoryService.createCategory("Cat1", null, null, null);
        Category cat2 = categoryService.createCategory("Cat2", null, null, null);

        Transaction tx = Transaction.builder()
                .id(1L).accountId(1L).originalPayee("Test").categoryId(cat1.getId())
                .amount(java.math.BigDecimal.ONE)
                .bookingDate(java.time.LocalDate.now())
                .valueDate(java.time.LocalDate.now())
                .checksum("c1")
                .build();
        store.getRoot().getTransactions().add(tx);

        categoryService.deleteCategory(cat1.getId(), cat2.getId());

        assertEquals(cat2.getId(), store.getRoot().getTransactions().get(0).getCategoryId());
    }
}
