package com.splitwise.split;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

/**
 * Splits the total evenly, rounding each share down to the cent and handing
 * the leftover cents to the first participants.
 *
 * <p>100.00 across 3 gives 33.34 / 33.33 / 33.33 — not 33.33 three times,
 * which would lose a cent.
 */
@Component
public class EqualSplitStrategy implements SplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.EQUAL;
    }

    @Override
    public List<ShareDto> split(BigDecimal total, List<SplitInput> inputs) {
        BigDecimal amount = SplitMath.requirePositiveTotal(total);
        List<SplitInput> participants = SplitMath.requireParticipants(inputs);

        int n = participants.size();
        BigDecimal base = amount.divide(BigDecimal.valueOf(n), SplitMath.MONEY_SCALE, RoundingMode.DOWN);

        List<BigDecimal> floored = Collections.nCopies(n, base);
        List<ShareDto> shares = SplitMath.distributeRemainder(participants, floored, amount);

        return SplitMath.verifySum(shares, amount, type());
    }
}
