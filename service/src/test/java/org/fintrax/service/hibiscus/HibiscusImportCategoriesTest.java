package org.fintrax.service.hibiscus;

import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HibiscusImportCategoriesTest {

    private static final String CATEGORIES_XML = "/org/fintrax/service/hibiscus/hibiscus-categories-test-data.xml";

    private final HibiscusXmlImportTestSupport support = new HibiscusXmlImportTestSupport();
    private Path tempDir;
    private StoreManager store;
    private HibiscusXmlImporter importer;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fintrax-test");
        store = new StoreManager(tempDir);
        importer = new HibiscusXmlImporter(store);
    }

    @AfterEach
    void tearDown() throws Exception {
        store.shutdown();
        support.deleteTempFiles(tempDir);
    }

    @Test
    void testImportCategoryCount() {
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);
        int categoriesBeforeImport = store.getRoot().getCategories().size();

        HibiscusXmlImporter.ImportResult result = importer.importFile(xmlFile, true, true);

        assertEquals(8, result.getCategoriesImported());
        assertEquals(categoriesBeforeImport + 8, store.getRoot().getCategories().size());
    }

    @Test
    void testImportTopLevelCategories() {
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);
        List<Category> categories = store.getRoot().getCategories();

        importer.importFile(xmlFile, true, true);

        String[] topLevelNames = {"Auto", "Lebensmittel", "Einkommen", "Miete und Wohnen"};
        for (String name : topLevelNames) {
            Category cat = store.getRoot().getCategories().stream()
                    .filter(c -> name.equals(c.getName()))
                    .findFirst().orElse(null);
            assertNotNull(cat, "Missing top-level category: " + name);
            assertNull(cat.getParentId(), name + " should have no parent");
        }
    }

    @Test
    void testImportCategoryHierarchy() {
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);

        importer.importFile(xmlFile, true, true);

        Category agip = store.getRoot().getCategories().stream()
                .filter(c -> "AGIP".equals(c.getName()))
                .findFirst().orElse(null);

        assertNotNull(agip);
        assertNotNull(agip.getParentId());

        Category kraftstoff = store.getRoot().getCategories().stream()
                .filter(c -> c.getId().equals(agip.getParentId()))
                .findFirst().orElse(null);

        assertNotNull(kraftstoff);
        assertEquals("Kraftstoff", kraftstoff.getName());
        assertNotNull(kraftstoff.getParentId());

        Category auto = store.getRoot().getCategories().stream()
                .filter(c -> c.getId().equals(kraftstoff.getParentId()))
                .findFirst().orElse(null);

        assertNotNull(auto);
        assertEquals("Auto", auto.getName());
        assertNull(auto.getParentId());
    }

    @Test
    void testImportChildCategoryHasParent() {
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);

        importer.importFile(xmlFile, true, true);

        String[] childNames = {"Kraftstoff", "AGIP", "ESSO", "Supermarkt"};
        for (String name : childNames) {
            Category cat = store.getRoot().getCategories().stream()
                    .filter(c -> name.equals(c.getName()))
                    .findFirst().orElse(null);
            assertNotNull(cat, "Missing category: " + name);
            assertNotNull(cat.getParentId(), name + " should have a parent");
        }
    }

    @Test
    void testImportWithoutCategories() {
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);
        int categoriesBeforeImport = store.getRoot().getCategories().size();

        HibiscusXmlImporter.ImportResult result = importer.importFile(xmlFile, false, false);

        assertEquals(0, result.getCategoriesImported());
        assertEquals(categoriesBeforeImport, store.getRoot().getCategories().size());
    }

    @Test
    void testSkipExistingCategories() {
        // Seed existing "Auto" and a user-added child "MyGarage" (not in XML)
        long parentId = 100L;
        Category parent = Category.builder()
                .id(parentId).name("Lebensmittel").parentId(null)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        store.getRoot().getCategories().add(parent);
        Category child = Category.builder()
                .id(200L).name("Supermarkt").parentId(parentId)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        store.getRoot().getCategories().add(parent);
        int categoriesBeforeImport = store.getRoot().getCategories().size();
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);

        // Act
        HibiscusXmlImporter.ImportResult result = importer.importFile(xmlFile, true, false);

        // Assert
        assertEquals(8, result.getCategoriesImported());
        assertEquals(categoriesBeforeImport + 8, store.getRoot().getCategories().size());
        List<Category> categories = store.getRoot().getCategories();
        // Lebensmittel and Supermarkt imports are skipped
        long parentCount = categories.stream()
                .filter(c -> parent.getName().equals(c.getName()))
                .count();
        assertEquals(1, parentCount, "Lebensmittel toplevel category must not be duplicated");
        long childCount = categories.stream()
                .filter(c -> child.getName().equals(c.getName()))
                .count();
        assertEquals(1, childCount, "Supermarkt child category must not be duplicated");
    }

    @Test
    void testImportPreservesUserAddedSubcategory() {
        // Seed existing "Auto" and a user-added child "MyGarage" (not in XML)
        long autoId = 100L;
        long myGarageId = 200L;
        Category auto = Category.builder()
                .id(autoId).name("Auto").parentId(null)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        Category myGarage = Category.builder()
                .id(myGarageId).name("MyGarage").parentId(autoId)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        store.getRoot().getCategories().add(auto);
        store.getRoot().getCategories().add(myGarage);
        int categoriesBeforeImport = store.getRoot().getCategories().size();
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);

        // Act
        HibiscusXmlImporter.ImportResult result = importer.importFile(xmlFile, true, false);

        // Assert
        assertEquals(8, result.getCategoriesImported());
        assertEquals(categoriesBeforeImport + 8, store.getRoot().getCategories().size());
        List<Category> categories = store.getRoot().getCategories();
        // 1. User-added subcategory still exists
        assertTrue(categories.stream()
                .anyMatch(c -> c.getId().equals(myGarage.getId()) && myGarage.getName().equals(c.getName())
                        && auto.getId().equals(c.getParentId())),
                "MyGarage category must still exist with correct parent");
        // 2. No duplicate Auto category
        long autoCount = categories.stream()
                .filter(c -> "Auto".equals(c.getName()))
                .count();
        assertEquals(1, autoCount, "Auto category must not be duplicated");
    }
}