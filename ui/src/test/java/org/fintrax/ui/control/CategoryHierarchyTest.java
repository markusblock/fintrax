package org.fintrax.ui.control;

import org.fintrax.model.Category;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryHierarchyTest {
    @Test
    void displayPathIncludesEveryAncestor() {
        Category food = Category.builder().id(1L).name("Food").build();
        Category groceries = Category.builder().id(2L).name("Groceries").parentId(food.getId()).build();
        Category supermarket = Category.builder().id(3L).name("Supermarket").parentId(groceries.getId()).build();

        assertEquals("Food → Groceries → Supermarket",
                CategoryHierarchy.displayPaths(List.of(supermarket, food, groceries)).apply(supermarket));
    }

    @Test
    void naturalOrderComparesDigitRunsNumericallyAndIgnoresCase() {
        List<String> sorted = List.of("Food 10", "food 2", "Food 1")
                .stream()
                .sorted(NaturalOrder.comparator(Locale.ENGLISH))
                .toList();

        assertEquals(List.of("Food 1", "food 2", "Food 10"), sorted);
    }
}
