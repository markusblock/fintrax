package org.fintrax.service;

import org.fintrax.fintx.PinStorage;
import org.fintrax.store.ResetGroup;
import org.fintrax.store.StoreManager;

import java.util.Set;

public class ResetService {
    private final StoreManager storeManager;
    private final PinStorage pinStorage;

    public ResetService(StoreManager storeManager, PinStorage pinStorage) {
        this.storeManager = storeManager;
        this.pinStorage = pinStorage;
    }

    public void reset(Set<ResetGroup> groups) {
        storeManager.reset(groups);
        if (groups.contains(ResetGroup.STORED_CREDENTIALS)) {
            pinStorage.clearAll();
        }
    }
}
