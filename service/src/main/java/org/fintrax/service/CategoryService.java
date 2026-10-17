package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
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

    public Category createCategory(String name, Long parentId, String comment) {
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

    public Category updateCategory(Long id, String name, String comment) {
        Category category = store.getCategory(id);
        if (category == null) {
            throw new IllegalArgumentException("Category not found: " + id);
        }

        if (!name.equals(category.getName()) && hasSiblingWithName(name, category.getParentId())) {
            throw new IllegalArgumentException("A sibling category with name '" + name + "' already exists");
        }

        category.setName(name);
        category.setComment(comment);
        category.setUpdatedAt(LocalDateTime.now());

        store.store(category);

        activityLogger.log(ActivityAction.UPDATE, EntityType.CATEGORY, id,
                "Updated category: " + name);

        log.info("Updated category {}", id);
        return category;
    }

    public void deleteCategory(Long id, Long reassignToId) {
        deleteCategory(id, reassignToId, true);
    }

    public void deleteCategory(Long id, Long reassignToId, boolean moveChildrenToParent) {
        Category category = store.getCategory(id);
        if (category == null) {
            throw new IllegalArgumentException("Category not found: " + id);
        }

        List<Category> children = getChildCategories(id);
        Set<Long> categoriesToDelete = new HashSet<>();
        categoriesToDelete.add(id);
        if (moveChildrenToParent) {
            for (Category child : children) {
                child.setParentId(category.getParentId());
                store.store(child);
            }
        } else {
            collectDescendantIds(id, categoriesToDelete);
        }

        store.getRoot().getTransactions().stream()
                .filter(t -> categoriesToDelete.contains(t.getCategoryId()))
                .forEach(t -> {
                    t.setCategoryId(reassignToId);
                    store.store(t);
                });

        store.getRoot().getCategories().removeIf(candidate -> categoriesToDelete.contains(candidate.getId()));
        store.store(store.getRoot().getCategories());

        activityLogger.log(ActivityAction.DELETE, EntityType.CATEGORY, id,
                "Deleted category: " + category.getName());

        log.info("Deleted category {} and reassigned transactions to {}", id, reassignToId);
    }

    private void collectDescendantIds(Long parentId, Set<Long> descendantIds) {
        for (Category child : getChildCategories(parentId)) {
            descendantIds.add(child.getId());
            collectDescendantIds(child.getId(), descendantIds);
        }
    }

    public List<Transaction> getTransactionsInCategory(Long categoryId) {
        return store.getRoot().getTransactions().stream()
                .filter(t -> categoryId.equals(t.getCategoryId()))
                .toList();
    }

    public List<Transaction> getTransactionsInCategoryAndDescendants(Long categoryId) {
        Set<Long> categoryIds = new HashSet<>();
        categoryIds.add(categoryId);
        collectDescendantIds(categoryId, categoryIds);

        return store.getRoot().getTransactions().stream()
                .filter(transaction -> categoryIds.contains(transaction.getCategoryId()))
                .toList();
    }
}
