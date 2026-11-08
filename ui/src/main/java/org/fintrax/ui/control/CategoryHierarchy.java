package org.fintrax.ui.control;

import org.fintrax.model.Category;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds full category paths without flattening parent/child relationships. */
public final class CategoryHierarchy {
    private static final String PATH_SEPARATOR = " → ";

    private CategoryHierarchy() {}

    public static Function<Category, String> displayPaths(Collection<Category> categories) {
        Map<Long, Category> categoriesById = categories.stream()
                .filter(category -> category.getId() != null)
                .collect(Collectors.toMap(Category::getId, Function.identity(), (first, ignored) -> first));
        return category -> displayPath(category, categoriesById::get);
    }

    public static String displayPath(Category category, Function<Long, Category> findById) {
        if (category == null) return "";
        Deque<String> path = new ArrayDeque<>();
        Set<Category> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Category current = category;
        while (current != null && visited.add(current)) {
            if (current.getName() != null && !current.getName().isBlank()) {
                path.addFirst(current.getName());
            }
            current = current.getParentId() == null ? null : findById.apply(current.getParentId());
        }
        return String.join(PATH_SEPARATOR, path);
    }
}
