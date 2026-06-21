package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
public class LabelService {
    private final StoreManager store;
    private final ActivityLogger activityLogger;
    private long nextId = 1;

    public LabelService(StoreManager store, ActivityLogger activityLogger) {
        this.store = store;
        this.activityLogger = activityLogger;
        initializeNextId();
    }

    private void initializeNextId() {
        nextId = store.getRoot().getLabels().stream()
                .mapToLong(Label::getId)
                .max()
                .orElse(0) + 1;
    }

    public Label createLabel(String name, String color, String comment) {
        boolean exists = store.getRoot().getLabels().stream()
                .anyMatch(l -> l.getName().equalsIgnoreCase(name));
        if (exists) {
            throw new IllegalArgumentException("Label with name already exists: " + name);
        }

        Label label = Label.builder()
                .id(nextId++)
                .name(name)
                .color(color)
                .comment(comment)
                .createdAt(LocalDateTime.now())
                .build();

        store.getRoot().getLabels().add(label);
        store.store(store.getRoot().getLabels());

        activityLogger.log(ActivityAction.CREATE, EntityType.LABEL, label.getId(),
                "Created label: " + name);

        log.info("Created label {} with name {}", label.getId(), name);
        return label;
    }

    public Optional<Label> getLabel(Long id) {
        return Optional.ofNullable(store.getLabel(id));
    }

    public List<Label> getAllLabels() {
        return List.copyOf(store.getRoot().getLabels());
    }

    public Label updateLabel(Long id, String name, String color, String comment) {
        Label label = store.getLabel(id);
        if (label == null) {
            throw new IllegalArgumentException("Label not found: " + id);
        }

        boolean nameExists = store.getRoot().getLabels().stream()
                .anyMatch(l -> !l.getId().equals(id) && l.getName().equalsIgnoreCase(name));
        if (nameExists) {
            throw new IllegalArgumentException("Label with name already exists: " + name);
        }

        label.setName(name);
        label.setColor(color);
        label.setComment(comment);

        store.store(label);

        activityLogger.log(ActivityAction.UPDATE, EntityType.LABEL, id,
                "Updated label: " + name);

        log.info("Updated label {}", id);
        return label;
    }

    public void deleteLabel(Long id) {
        Label label = store.getLabel(id);
        if (label == null) {
            throw new IllegalArgumentException("Label not found: " + id);
        }

        store.getRoot().getTransactions().forEach(t -> t.getLabelIds().remove(id));
        store.store(store.getRoot().getTransactions());

        store.getRoot().getLabels().remove(label);
        store.store(store.getRoot().getLabels());

        activityLogger.log(ActivityAction.DELETE, EntityType.LABEL, id,
                "Deleted label: " + label.getName());

        log.info("Deleted label {} and removed from transactions", id);
    }

    public List<Transaction> getTransactionsWithLabel(Long labelId) {
        return store.getRoot().getTransactions().stream()
                .filter(t -> t.getLabelIds().contains(labelId))
                .toList();
    }
}
