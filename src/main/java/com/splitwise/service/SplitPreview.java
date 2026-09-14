package com.splitwise.service;

import com.splitwise.split.SplitType;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the expense form shows before submission.
 *
 * <p>{@code assigned} and {@code remaining} are pesos for EXACT and percent
 * for PERCENTAGE; EQUAL has nothing left over by construction.
 */
public record SplitPreview(SplitType type,
                           List<PreviewRow> rows,
                           BigDecimal total,
                           BigDecimal assigned,
                           BigDecimal remaining,
                           boolean balanced,
                           String status,
                           String problem) {

    /**
     * @param amount the share the strategy computed, or null when the inputs
     *               do not yet add up and no split could be run
     */
    public record PreviewRow(Long userId, String name, BigDecimal amount, BigDecimal value) {
    }

    public boolean hasProblem() {
        return problem != null;
    }

    static SplitPreview problem(SplitType type, String problem) {
        return new SplitPreview(type, List.of(), null, null, null, false, null, problem);
    }
}
