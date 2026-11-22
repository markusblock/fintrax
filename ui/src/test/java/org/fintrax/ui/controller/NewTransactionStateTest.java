package org.fintrax.ui.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewTransactionStateTest {
    @Test
    void onlyTransactionsImportedThisSessionStartUnviewed() {
        NewTransactionState state = new NewTransactionState();

        state.markNew(List.of(11L, 12L));

        assertTrue(state.isUnviewedNew(11L));
        assertTrue(state.isUnviewedNew(12L));
        assertFalse(state.isUnviewedNew(13L));
    }

    @Test
    void viewingOneNewTransactionLeavesOtherNewTransactionsUnviewed() {
        NewTransactionState state = new NewTransactionState();
        state.markNew(List.of(11L, 12L));

        state.markViewed(11L);

        assertFalse(state.isUnviewedNew(11L));
        assertTrue(state.isUnviewedNew(12L));
    }

    @Test
    void aFreshSessionStartsWithoutUnviewedTransactions() {
        NewTransactionState previousSession = new NewTransactionState();
        previousSession.markNew(List.of(11L));

        NewTransactionState nextSession = new NewTransactionState();

        assertFalse(nextSession.isUnviewedNew(11L));
    }
}
