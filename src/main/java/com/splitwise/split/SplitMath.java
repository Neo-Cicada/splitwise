package com.splitwise.split;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared money arithmetic for the split strategies.
 *
 * <p>Every amount is a BigDecimal at scale 2. Comparisons use compareTo, never
 * equals, because 100.0 and 100.00 are equal in value but not as objects.
 */
final class SplitMath {

    static final BigDecimal ONE_CENT = new BigDecimal("0.01");
    static final int MONEY_SCALE = 2;

    private SplitMath() {
    }

    /** Validates a monetary input and normalises it to scale 2. */
    static BigDecimal requireMoney(BigDecimal value, String what) {
        if (value == null) {
            throw new InvalidSplitException(what + " is required.");
        }
        if (value.scale() > MONEY_SCALE) {
            throw new InvalidSplitException(
                    what + " has more precision than cents: " + value.toPlainString() + ".");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    static BigDecimal requirePositiveTotal(BigDecimal total) {
        BigDecimal normalised = requireMoney(total, "Total");
        if (normalised.signum() <= 0) {
            throw new InvalidSplitException("Total must be greater than zero.");
        }
        return normalised;
    }

    static List<SplitInput> requireParticipants(List<SplitInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new InvalidSplitException("A split needs at least one participant.");
        }
        if (inputs.stream().anyMatch(input -> input == null || input.userId() == null)) {
            throw new InvalidSplitException("Every participant needs a user id.");
        }
        long distinct = inputs.stream().map(SplitInput::userId).distinct().count();
        if (distinct != inputs.size()) {
            throw new InvalidSplitException("The same participant appears twice in one split.");
        }
        return inputs;
    }

    /**
     * Hands the leftover cents out one at a time to the first participants.
     *
     * <p>Rounding each share down leaves the pot short by a few cents; someone
     * has to absorb them, and giving them to the earliest participants is
     * deterministic and keeps every share within one cent of fair.
     */
    static List<ShareDto> distributeRemainder(List<SplitInput> inputs,
                                              List<BigDecimal> flooredAmounts,
                                              BigDecimal total) {
        BigDecimal allocated = flooredAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainder = total.subtract(allocated);

        if (remainder.signum() < 0) {
            throw new InvalidSplitException(
                    "Shares overshoot the total by " + remainder.negate().toPlainString() + ".");
        }

        int leftoverCents = remainder.movePointRight(MONEY_SCALE).intValueExact();
        if (leftoverCents > inputs.size()) {
            throw new InvalidSplitException(
                    "Cannot distribute " + remainder.toPlainString() + " across "
                            + inputs.size() + " participants.");
        }

        List<ShareDto> shares = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            BigDecimal amount = i < leftoverCents
                    ? flooredAmounts.get(i).add(ONE_CENT)
                    : flooredAmounts.get(i);
            shares.add(new ShareDto(inputs.get(i).userId(), amount));
        }
        return shares;
    }

    /** Last line of defence: the shares must add up to the total, to the cent. */
    static List<ShareDto> verifySum(List<ShareDto> shares, BigDecimal total, SplitType type) {
        BigDecimal sum = shares.stream()
                .map(ShareDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(total) != 0) {
            throw new InvalidSplitException(
                    type + " split produced " + sum.toPlainString()
                            + " but the total is " + total.toPlainString() + ".");
        }
        return shares;
    }
}
