package org.fintrax.store;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.Category;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CategorySeeder {
    private static final String[][] DEFAULT_TREE = {
            {"Einnahmen", null},
            {"Gehalt", "Einnahmen"},
            {"Freelance", "Einnahmen"},
            {"Sonstige Einnahmen", "Einnahmen"},
            {"Wohnen", null},
            {"Miete", "Wohnen"},
            {"Nebenkosten", "Wohnen"},
            {"Versicherungen", "Wohnen"},
            {"Ernährung", null},
            {"Lebensmittel", "Ernährung"},
            {"Restaurants", "Ernährung"},
            {"Transport", null},
            {"Kraftstoff", "Transport"},
            {"ÖPNV", "Transport"},
            {"KFZ-Wartung", "Transport"},
            {"Gesundheit", null},
            {"Arzt/Apotheke", "Gesundheit"},
            {"Fitness", "Gesundheit"},
            {"Einkaufen", null},
            {"Kleidung", "Einkaufen"},
            {"Elektronik", "Einkaufen"},
            {"Haushalt", "Einkaufen"},
            {"Freizeit", null},
            {"Unterhaltung", "Freizeit"},
            {"Abos", "Freizeit"},
            {"Reisen", "Freizeit"},
            {"Finanzen", null},
            {"Sparen", "Finanzen"},
            {"Investitionen", "Finanzen"},
            {"Gebühren/Steuern", "Finanzen"},
            {"Sonstiges", null},
            {"Geschenke", "Sonstiges"},
            {"Verschiedenes", "Sonstiges"}
    };

    private long nextId = 1;

    public void seed(DataRoot root) {
        if (!root.getCategories().isEmpty()) {
            log.info("Categories already exist, skipping seed");
            return;
        }

        log.info("Seeding default German category tree");
        Map<String, Category> nameMap = new HashMap<>();

        for (String[] entry : DEFAULT_TREE) {
            String name = entry[0];
            String parentName = entry[1];

            Category category = Category.builder()
                    .id(nextId++)
                    .name(name)
                    .parentId(parentName != null ? nameMap.get(parentName).getId() : null)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            root.getCategories().add(category);
            nameMap.put(name, category);
        }

        log.info("Seeded {} categories", root.getCategories().size());
    }
}
