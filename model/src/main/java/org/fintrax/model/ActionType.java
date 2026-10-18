package org.fintrax.model;

public enum ActionType {
    ASSIGN_CATEGORY,
    ADD_LABEL,
    /** Legacy value retained so stored rules can be read; note actions are no longer supported. */
    SET_NOTE
}
