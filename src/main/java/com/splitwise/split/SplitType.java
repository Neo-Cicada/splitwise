package com.splitwise.split;

/**
 * How an expense is divided among participants. Persisted as the
 * {@code expenses.split_type} VARCHAR(20).
 */
public enum SplitType {
    EQUAL,
    EXACT,
    PERCENTAGE
}
