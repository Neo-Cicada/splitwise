package com.splitwise.split;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Takes the amounts as given. Nothing is rounded or redistributed — if the
 * amounts do not add up to the total, that is the caller's error.
 */
@Component
public class ExactSplitStrategy implements SplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.EXACT;
    }

    @Override
    public List<ShareDto> split(BigDecimal total, List<SplitInput> inputs) {
        BigDecimal amount = SplitMath.requirePositiveTotal(total);
        List<SplitInput> participants = SplitMath.requireParticipants(inputs);

        List<ShareDto> shares = participants.stream()
                .map(input -> {
                    BigDecimal share = SplitMath.requireMoney(
                            input.value(), "Share for participant " + input.userId());
                    if (share.signum() < 0) {
                        throw new InvalidSplitException(
                                "Share for participant " + input.userId() + " is negative.");
                    }
                    return new ShareDto(input.userId(), share);
                })
                .toList();

        return SplitMath.verifySum(shares, amount, type());
    }
}
