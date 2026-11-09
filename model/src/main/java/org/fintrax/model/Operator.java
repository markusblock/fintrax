package org.fintrax.model;

public enum Operator {
    // Eclipse Store persists enum ordinals; preserve the original order and append new operators.
    CONTAINS, REGEX, EQUALS, AMOUNT_RANGE, CONTAINS_NOT, NOT_REGEX, NOT_EQUALS
}
