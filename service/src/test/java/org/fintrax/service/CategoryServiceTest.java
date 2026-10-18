package org.fintrax.service;

import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

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
        Category cat = categoryService.createCategory("Food", null, null);

        assertNotNull(cat.getId());
        assertEquals("Food", cat.getName());
        assertNull(cat.getParentId());
    }

    @Test
    void testCreateChildCategory() {
        Category parent = categoryService.createCategory("Food", null, null);
        Category child = categoryService.createCategory("Groceries", parent.getId(), null);

        assertEquals(parent.getId(), child.getParentId());
    }

    @Test
    void testMaxDepth() {
        Category c1 = categoryService.createCategory("L1", null, null);
        Category c2 = categoryService.createCategory("L2", c1.getId(), null);
        Category c3 = categoryService.createCategory("L3", c2.getId(), null);
        Category c4 = categoryService.createCategory("L4", c3.getId(), null);
        Category c5 = categoryService.createCategory("L5", c4.getId(), null);

        assertThrows(IllegalArgumentException.class, () ->
                categoryService.createCategory("L6", c5.getId(), null));
    }

    @Test
    void testGetRootCategories() {
        int initialRootCount = categoryService.getRootCategories().size();
        categoryService.createCategory("TestRoot1", null, null);
        Category root2 = categoryService.createCategory("TestRoot2", null, null);
        categoryService.createCategory("Child", root2.getId(), null);

        assertEquals(initialRootCount + 2, categoryService.getRootCategories().size());
    }

    @Test
    void testCreateDuplicateSiblingRejected() {
        categoryService.createCategory("Food", null, null);

        assertThrows(IllegalArgumentException.class, () ->
                categoryService.createCategory("Food", null, null));
    }

    @Test
    void testCreateSameNameUnderDifferentParentAllowed() {
        Category parent1 = categoryService.createCategory("Parent1", null, null);
        Category parent2 = categoryService.createCategory("Parent2", null, null);

        categoryService.createCategory("Shared", parent1.getId(), null);
        Category child2 = categoryService.createCategory("Shared", parent2.getId(), null);

        assertEquals(parent2.getId(), child2.getParentId());
    }

    @Test
    void testUpdateCategoryRenameToDuplicateSiblingRejected() {
        Category cat1 = categoryService.createCategory("Cat1", null, null);
        categoryService.createCategory("Cat2", null, null);

        assertThrows(IllegalArgumentException.class, () ->
                categoryService.updateCategory(cat1.getId(), "Cat2", null));
    }

    @Test
    void testUpdateCategoryRenameToNameUsedUnderAnotherParentAllowed() {
        Category parent1 = categoryService.createCategory("Parent1", null, null);
        Category parent2 = categoryService.createCategory("Parent2", null, null);
        Category cat = categoryService.createCategory("Old", parent1.getId(), null);
        categoryService.createCategory("New", parent2.getId(), null);

        categoryService.updateCategory(cat.getId(), "New", null);

        assertEquals("New", categoryService.getCategory(cat.getId()).get().getName());
    }

    @Test
    void testDeleteCategoryReassignsTransactions() {
        CategoryService txService = new CategoryService(store, activityLogger);
        Category cat1 = categoryService.createCategory("Cat1", null, null);
        Category cat2 = categoryService.createCategory("Cat2", null, null);

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

    @Test
    void deleteCategoryWithoutMovingChildrenDeletesEntireSubtree() {
        Category parent = categoryService.createCategory("Parent", null, null);
        Category category = categoryService.createCategory("Category", parent.getId(), null);
        Category child = categoryService.createCategory("Child", category.getId(), null);
        Category grandchild = categoryService.createCategory("Grandchild", child.getId(), null);
        Transaction transaction = Transaction.builder().id(1L).categoryId(grandchild.getId()).build();
        store.getRoot().getTransactions().add(transaction);

        categoryService.deleteCategory(category.getId(), null, false);

        assertTrue(categoryService.getCategory(category.getId()).isEmpty());
        assertTrue(categoryService.getCategory(child.getId()).isEmpty());
        assertTrue(categoryService.getCategory(grandchild.getId()).isEmpty());
        assertTrue(categoryService.getCategory(parent.getId()).isPresent());
        assertNull(transaction.getCategoryId());
    }

    @Test
    void deleteCategoryMovingChildrenPromotesThemToDeletedCategoriesParent() {
        Category parent = categoryService.createCategory("Parent", null, null);
        Category category = categoryService.createCategory("Category", parent.getId(), null);
        Category child = categoryService.createCategory("Child", category.getId(), null);
        Category grandchild = categoryService.createCategory("Grandchild", child.getId(), null);

        categoryService.deleteCategory(category.getId(), null, true);

        assertTrue(categoryService.getCategory(category.getId()).isEmpty());
        assertEquals(parent.getId(), categoryService.getCategory(child.getId()).orElseThrow().getParentId());
        assertEquals(child.getId(), categoryService.getCategory(grandchild.getId()).orElseThrow().getParentId());
    }

    @Test
    void deleteTopLevelCategoryMovingChildrenPromotesThemToTopLevel() {
        Category category = categoryService.createCategory("Category", null, null);
        Category child = categoryService.createCategory("Child", category.getId(), null);
        Category grandchild = categoryService.createCategory("Grandchild", child.getId(), null);

        categoryService.deleteCategory(category.getId(), null, true);

        assertTrue(categoryService.getCategory(category.getId()).isEmpty());
        assertNull(categoryService.getCategory(child.getId()).orElseThrow().getParentId());
        assertEquals(child.getId(), categoryService.getCategory(grandchild.getId()).orElseThrow().getParentId());
    }

    @Test
    void getTransactionsInCategoryAndDescendantsIncludesOnlyCategorySubtree() {
        Category category = categoryService.createCategory("Category", null, null);
        Category child = categoryService.createCategory("Child", category.getId(), null);
        Category grandchild = categoryService.createCategory("Grandchild", child.getId(), null);
        Category unrelated = categoryService.createCategory("Unrelated", null, null);
        store.getRoot().getTransactions().addAll(List.of(
                Transaction.builder().id(1L).categoryId(category.getId()).build(),
                Transaction.builder().id(2L).categoryId(child.getId()).build(),
                Transaction.builder().id(3L).categoryId(grandchild.getId()).build(),
                Transaction.builder().id(4L).categoryId(unrelated.getId()).build()));

        List<Long> transactionIds = categoryService.getTransactionsInCategoryAndDescendants(category.getId())
                .stream()
                .map(Transaction::getId)
                .toList();

        assertEquals(List.of(1L, 2L, 3L), transactionIds);
    }
}
