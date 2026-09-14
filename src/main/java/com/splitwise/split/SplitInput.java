package com.splitwise.split;

import java.math.BigDecimal;

/**
 * One participant in a split.
 *
 * <p>The meaning of {@code value} depends on the strategy:
 * <ul>
 *   <li>{@link SplitType#EQUAL} — ignored, may be {@code null}</li>
 *   <li>{@link SplitType#EXACT} — the exact amount this participant owes</li>
 *   <li>{@link SplitType#PERCENTAGE} — this participant's percentage of the total</li>
 * </ul>
 */
public record SplitInput(Long userId, BigDecimal value) {

    /** Convenience for EQUAL splits, where no per-participant value is needed. */
    public static SplitInput of(Long userId) {
        return new SplitInput(userId, null);
    }

    public static SplitInput of(Long userId, String value) {
        return new SplitInput(userId, new BigDecimal(value));
    }
}
