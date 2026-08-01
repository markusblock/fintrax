package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class CategoryService {
    private static final int MAX_DEPTH = 5;
    private final StoreManager store;
    private final ActivityLogger activityLogger;
    private long nextId = 1;

    public CategoryService(StoreManager store, ActivityLogger activityLogger) {
        this.store = store;
        this.activityLogger = activityLogger;
        initializeNextId();
    }

    private void initializeNextId() {
        nextId = store.getRoot().getCategories().stream()
                .mapToLong(Category::getId)
                .max()
                .orElse(0) + 1;
    }

    public Category createCategory(String name, Long parentId, String color, String comment) {
        if (parentId != null) {
            int depth = calculateDepth(parentId);
            if (depth >= MAX_DEPTH) {
                throw new IllegalArgumentException("Maximum category depth (" + MAX_DEPTH + ") exceeded");
            }
        }

        if (hasSiblingWithName(name, parentId)) {
            throw new IllegalArgumentException("A sibling category with name '" + name + "' already exists");
        }

        Category category = Category.builder()
                .id(nextId++)
                .name(name)
                .parentId(parentId)
                .color(color)
                .comment(comment)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        store.getRoot().getCategories().add(category);
        store.store(store.getRoot().getCategories());

        activityLogger.log(ActivityAction.CREATE, EntityType.CATEGORY, category.getId(),
                "Created category: " + name);

        log.info("Created category {} with name {}", category.getId(), name);
        return category;
    }

    private boolean hasSiblingWithName(String name, Long parentId) {
        return store.getRoot().getCategories().stream()
                .anyMatch(c -> name.equals(c.getName()) && Objects.equals(parentId, c.getParentId()));
    }

    private int calculateDepth(Long parentId) {
        int depth = 0;
        Long currentId = parentId;

        while (currentId != null) {
            Category parent = store.getCategory(currentId);
            if (parent == null) break;
            depth++;
            currentId = parent.getParentId();
        }

        return depth;
    }

    public Optional<Category> getCategory(Long id) {
        return Optional.ofNullable(store.getCategory(id));
    }

    public List<Category> getAllCategories() {
        return List.copyOf(store.getRoot().getCategories());
    }

    public List<Category> getRootCategories() {
        return store.getRoot().getCategories().stream()
                .filter(c -> c.getParentId() == null)
                .toList();
    }

    public List<Category> getChildCategories(Long parentId) {
        return store.getRoot().getCategories().stream()
                .filter(c -> parentId.equals(c.getParentId()))
                .toList();
    }

    public Category updateCategory(Long id, String name, String color, String comment) {
        Category category = store.getCategory(id);
        if (category == null) {
            throw new IllegalArgumentException("Category not found: " + id);
        }

        if (!name.equals(category.getName()) && hasSiblingWithName(name, category.getParentId())) {
            throw new IllegalArgumentException("A sibling category with name '" + name + "' already exists");
        }

        category.setName(name);
        category.setColor(color);
        category.setComment(comment);
        category.setUpdatedAt(LocalDateTime.now());

        store.store(category);

        activityLogger.log(ActivityAction.UPDATE, EntityType.CATEGORY, id,
                "Updated category: " + name);

        log.info("Updated category {}", id);
        return category;
    }

    public void deleteCategory(Long id, Long reassignToId) {
        Category category = store.getCategory(id);
        if (category == null) {
            throw new IllegalArgumentException("Category not found: " + id);
        }

        List<Category> children = getChildCategories(id);
        for (Category child : children) {
            child.setParentId(category.getParentId());
            store.store(child);
        }

        store.getRoot().getTransactions().stream()
                .filter(t -> id.equals(t.getCategoryId()))
                .forEach(t -> {
                    t.setCategoryId(reassignToId);
                    store.store(t);
                });

        store.getRoot().getCategories().remove(category);
        store.store(store.getRoot().getCategories());

        activityLogger.log(ActivityAction.DELETE, EntityType.CATEGORY, id,
                "Deleted category: " + category.getName());

        log.info("Deleted category {} and reassigned transactions to {}", id, reassignToId);
    }

    public List<Transaction> getTransactionsInCategory(Long categoryId) {
        return store.getRoot().getTransactions().stream()
                .filter(t -> categoryId.equals(t.getCategoryId()))
                .toList();
    }
}
