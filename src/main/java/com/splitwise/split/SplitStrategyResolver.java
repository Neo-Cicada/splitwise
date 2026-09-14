package com.splitwise.split;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up the strategy for a split type. Spring injects every SplitStrategy
 * bean, so adding a new one is a matter of writing the class — there is no
 * switch or if/else here to forget to update.
 */
@Component
public class SplitStrategyResolver {

    private final Map<SplitType, SplitStrategy> strategies;

    public SplitStrategyResolver(List<SplitStrategy> strategies) {
        Map<SplitType, SplitStrategy> byType = new EnumMap<>(SplitType.class);
        for (SplitStrategy strategy : strategies) {
            SplitStrategy previous = byType.put(strategy.type(), strategy);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two strategies claim " + strategy.type() + ": "
                                + previous.getClass().getName() + " and "
                                + strategy.getClass().getName());
            }
        }
        this.strategies = Map.copyOf(byType);
    }

    public SplitStrategy resolve(SplitType type) {
        SplitStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new InvalidSplitException("No split strategy for " + type + ".");
        }
        return strategy;
    }

    /** Convenience for callers that have a type, a total and participants. */
    public List<ShareDto> split(SplitType type, java.math.BigDecimal total, List<SplitInput> inputs) {
        return resolve(type).split(total, inputs);
    }
}
