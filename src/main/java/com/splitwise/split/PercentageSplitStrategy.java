package com.splitwise.split;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Splits by percentage. The percentages must sum to exactly 100; the resulting
 * amounts are rounded down and the leftover cents distributed exactly as in
 * {@link EqualSplitStrategy}.
 */
@Component
public class PercentageSplitStrategy implements SplitStrategy {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Override
    public SplitType type() {
        return SplitType.PERCENTAGE;
    }

    @Override
    public List<ShareDto> split(BigDecimal total, List<SplitInput> inputs) {
        BigDecimal amount = SplitMath.requirePositiveTotal(total);
        List<SplitInput> participants = SplitMath.requireParticipants(inputs);

        BigDecimal sumOfPercentages = BigDecimal.ZERO;
        for (SplitInput input : participants) {
            if (input.value() == null) {
                throw new InvalidSplitException(
                        "Participant " + input.userId() + " has no percentage.");
            }
            if (input.value().signum() < 0) {
                throw new InvalidSplitException(
                        "Participant " + input.userId() + " has a negative percentage.");
            }
            sumOfPercentages = sumOfPercentages.add(input.value());
        }

        if (sumOfPercentages.compareTo(ONE_HUNDRED) != 0) {
            throw new InvalidSplitException(
                    "Percentages must sum to 100 but sum to " + sumOfPercentages.toPlainString() + ".");
        }

        List<BigDecimal> floored = participants.stream()
                .map(input -> amount.multiply(input.value())
                        .divide(ONE_HUNDRED, SplitMath.MONEY_SCALE, RoundingMode.DOWN))
                .toList();

        List<ShareDto> shares = SplitMath.distributeRemainder(participants, floored, amount);

        return SplitMath.verifySum(shares, amount, type());
    }
}
