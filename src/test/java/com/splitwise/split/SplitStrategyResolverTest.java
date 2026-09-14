package com.splitwise.split;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitStrategyResolverTest {

    private final SplitStrategyResolver resolver = new SplitStrategyResolver(List.of(
            new EqualSplitStrategy(),
            new ExactSplitStrategy(),
            new PercentageSplitStrategy()));

    @Test
    void resolvesEveryDeclaredSplitType() {
        for (SplitType type : SplitType.values()) {
            assertThat(resolver.resolve(type).type()).isEqualTo(type);
        }
    }

    @Test
    void splitsThroughTheResolvedStrategy() {
        List<ShareDto> shares = resolver.split(
                SplitType.EQUAL, new BigDecimal("100.00"),
                List.of(SplitInput.of(1L), SplitInput.of(2L), SplitInput.of(3L)));

        assertThat(shares).extracting(ShareDto::amount)
                .containsExactly(
                        new BigDecimal("33.34"),
                        new BigDecimal("33.33"),
                        new BigDecimal("33.33"));
    }

    @Test
    void rejectsTwoStrategiesClaimingTheSameType() {
        List<SplitStrategy> duplicated = List.of(new EqualSplitStrategy(), new EqualSplitStrategy());

        assertThatThrownBy(() -> new SplitStrategyResolver(duplicated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EQUAL");
    }

    @Test
    void reportsAMissingStrategy() {
        SplitStrategyResolver sparse = new SplitStrategyResolver(List.of(new EqualSplitStrategy()));

        assertThatThrownBy(() -> sparse.resolve(SplitType.EXACT))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("No split strategy");
    }
}
