package org.fintrax.service.hibiscus;

import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        store.getRoot().getCategories().add(child);
        int categoriesBeforeImport = store.getRoot().getCategories().size();
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);

        // Act
        HibiscusXmlImporter.ImportResult result = importer.importFile(xmlFile, true, false);

        // Assert
        assertEquals(6, result.getCategoriesImported());
        assertEquals(categoriesBeforeImport + 6, store.getRoot().getCategories().size());
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
        assertEquals(7, result.getCategoriesImported());
        assertEquals(categoriesBeforeImport + 7, store.getRoot().getCategories().size());
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

    @Test
    void testImportResultIsIndependentOfXmlOrder() throws Exception {
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);

        importer.importFile(xmlFile, true, true);
        Set<String> normalShape = treeShape(store);

        File reversed = reverseObjects(xmlFile);
        Path reversedDir = Files.createTempDirectory("fintrax-reversed");
        StoreManager reversedStore = new StoreManager(reversedDir);
        try {
            new HibiscusXmlImporter(reversedStore).importFile(reversed, true, true);
            assertEquals(normalShape, treeShape(reversedStore),
                    "Category tree must not depend on XML object order");
        } finally {
            reversedStore.shutdown();
            support.deleteTempFiles(reversedDir);
        }
    }

    @Test
    void testReimportInReversedOrderDoesNotDuplicate() throws Exception {
        File xmlFile = support.loadXmlTestData(CATEGORIES_XML);
        importer.importFile(xmlFile, true, false);
        Set<String> shapeAfterFirst = treeShape(store);
        assertEquals(8, store.getRoot().getCategories().size());

        File reversed = reverseObjects(xmlFile);
        HibiscusXmlImporter.ImportResult result = importer.importFile(reversed, true, false);

        assertEquals(0, result.getCategoriesImported());
        assertEquals(8, store.getRoot().getCategories().size());
        assertEquals(shapeAfterFirst, treeShape(store));
    }

    @Test
    void testRejectDuplicateSiblingCategories() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <objects>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="2">
                    <name type="java.lang.String">Auto</name>
                  </object>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="3">
                    <parent_id type="java.lang.Integer">2</parent_id>
                    <name type="java.lang.String">Kraftstoff</name>
                  </object>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="4">
                    <parent_id type="java.lang.Integer">2</parent_id>
                    <name type="java.lang.String">Kraftstoff</name>
                  </object>
                </objects>
                """;
        File xmlFile = writeXml(xml);

        assertThrows(IllegalArgumentException.class, () -> importer.importFile(xmlFile, true, true));
    }

    @Test
    void testRejectDuplicateTopLevelCategories() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <objects>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="2">
                    <name type="java.lang.String">Auto</name>
                  </object>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="3">
                    <name type="java.lang.String">Auto</name>
                  </object>
                </objects>
                """;
        File xmlFile = writeXml(xml);

        assertThrows(IllegalArgumentException.class, () -> importer.importFile(xmlFile, true, true));
    }

    @Test
    void testSameNameUnderDifferentParentsAccepted() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <objects>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="2">
                    <name type="java.lang.String">Auto</name>
                  </object>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="3">
                    <parent_id type="java.lang.Integer">2</parent_id>
                    <name type="java.lang.String">Auto</name>
                  </object>
                </objects>
                """;
        File xmlFile = writeXml(xml);

        HibiscusXmlImporter.ImportResult result = importer.importFile(xmlFile, true, true);

        assertEquals(2, result.getCategoriesImported());
    }

    @Test
    void testRejectUnresolvedParent() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <objects>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="3">
                    <parent_id type="java.lang.Integer">99</parent_id>
                    <name type="java.lang.String">Orphan</name>
                  </object>
                </objects>
                """;
        File xmlFile = writeXml(xml);

        assertThrows(IllegalArgumentException.class, () -> importer.importFile(xmlFile, true, true));
    }

    @Test
    void testRejectCircularParentReferences() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <objects>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="2">
                    <parent_id type="java.lang.Integer">3</parent_id>
                    <name type="java.lang.String">A</name>
                  </object>
                  <object type="de.willuhn.jameica.hbci.server.UmsatzTypImpl" id="3">
                    <parent_id type="java.lang.Integer">2</parent_id>
                    <name type="java.lang.String">B</name>
                  </object>
                </objects>
                """;
        File xmlFile = writeXml(xml);

        assertThrows(IllegalArgumentException.class, () -> importer.importFile(xmlFile, true, true));
    }

    private Set<String> treeShape(StoreManager store) {
        Map<Long, String> idToName = new HashMap<>();
        store.getRoot().getCategories().forEach(c -> idToName.put(c.getId(), c.getName()));
        return store.getRoot().getCategories().stream()
                .map(c -> c.getName() + " -> " + (c.getParentId() == null ? "ROOT" : idToName.get(c.getParentId())))
                .collect(Collectors.toSet());
    }

    private File reverseObjects(File xmlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        Element docRoot = doc.getDocumentElement();
        List<Element> objects = new ArrayList<>();
        NodeList nodes = docRoot.getElementsByTagName("object");
        for (int i = 0; i < nodes.getLength(); i++) {
            objects.add((Element) nodes.item(i));
        }
        for (Element el : objects) {
            docRoot.removeChild(el);
        }
        for (int i = objects.size() - 1; i >= 0; i--) {
            docRoot.appendChild(objects.get(i));
        }
        File out = new File(tempDir.toFile(), "categories-reversed.xml");
        TransformerFactory.newInstance().newTransformer()
                .transform(new DOMSource(doc), new StreamResult(out));
        return out;
    }

    private File writeXml(String content) throws Exception {
        File out = new File(tempDir.toFile(), "inline-categories.xml");
        Files.writeString(out.toPath(), content);
        return out;
    }
}