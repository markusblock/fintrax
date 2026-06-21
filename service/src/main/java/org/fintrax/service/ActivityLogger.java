package org.fintrax.service;

import lombok.extern.slf4j.Slf4j;
import org.fintrax.model.*;
import org.fintrax.store.StoreManager;

import java.time.LocalDateTime;

@Slf4j
public class ActivityLogger {
    private static final int MAX_LOGS = 10_000;
    private final StoreManager store;
    private long nextId = 1;

    public ActivityLogger(StoreManager store) {
        this.store = store;
        initializeNextId();
    }

    private void initializeNextId() {
        nextId = store.getRoot().getActivityLogs().stream()
                .mapToLong(ActivityLog::getId)
                .max()
                .orElse(0) + 1;
    }

    public void log(ActivityAction action, EntityType entityType, Long entityId, String description) {
        ActivityLog activityLog = ActivityLog.builder()
                .id(nextId++)
                .timestamp(LocalDateTime.now())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .build();

        store.getRoot().getActivityLogs().add(activityLog);

        if (store.getRoot().getActivityLogs().size() > MAX_LOGS) {
            store.getRoot().getActivityLogs().remove(0);
        }

        store.store(store.getRoot().getActivityLogs());

        log.debug("Activity: {} {} {} - {}", action, entityType, entityId, description);
    }
}
