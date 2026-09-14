package com.splitwise.split;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PercentageSplitStrategyTest {

    private final PercentageSplitStrategy strategy = new PercentageSplitStrategy();

    private static BigDecimal sum(List<ShareDto> shares) {
        return shares.stream().map(ShareDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("33 / 33 / 34 of 100.00 sums to exactly 100.00")
    void splitsThirtyThreeThirtyThreeThirtyFour() {
        List<ShareDto> shares = strategy.split(new BigDecimal("100.00"), List.of(
                SplitInput.of(1L, "33"),
                SplitInput.of(2L, "33"),
                SplitInput.of(3L, "34")));

        assertThat(shares).extracting(ShareDto::amount)
                .containsExactly(
                        new BigDecimal("33.00"),
                        new BigDecimal("33.00"),
                        new BigDecimal("34.00"));
        assertThat(sum(shares)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("fractional percentages still sum to the total, leftover cents redistributed")
    void redistributesLeftoverCents() {
        List<ShareDto> shares = strategy.split(new BigDecimal("100.00"), List.of(
                SplitInput.of(1L, "33.33"),
                SplitInput.of(2L, "33.33"),
                SplitInput.of(3L, "33.34")));

        assertThat(sum(shares)).isEqualByComparingTo("100.00");
        assertThat(shares).extracting(ShareDto::amount)
                .containsExactly(
                        new BigDecimal("33.33"),
                        new BigDecimal("33.33"),
                        new BigDecimal("33.34"));
    }

    @Test
    @DisplayName("a total that does not divide cleanly still sums exactly")
    void awkwardTotalStillSumsExactly() {
        List<ShareDto> shares = strategy.split(new BigDecimal("0.05"), List.of(
                SplitInput.of(1L, "33.33"),
                SplitInput.of(2L, "33.33"),
                SplitInput.of(3L, "33.34")));

        assertThat(sum(shares)).isEqualByComparingTo("0.05");
    }

    @Test
    @DisplayName("rejects percentages that do not sum to 100")
    void rejectsPercentagesThatDoNotSumToOneHundred() {
        List<SplitInput> ninetyNine = List.of(
                SplitInput.of(1L, "33"),
                SplitInput.of(2L, "33"),
                SplitInput.of(3L, "33"));

        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), ninetyNine))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("sum to 100")
                .hasMessageContaining("99");
    }

    @Test
    void rejectsPercentagesOverOneHundred() {
        List<SplitInput> tooMuch = List.of(
                SplitInput.of(1L, "60"),
                SplitInput.of(2L, "60"));

        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), tooMuch))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("120");
    }

    @Test
    void rejectsAMissingPercentage() {
        List<SplitInput> missing = List.of(
                new SplitInput(1L, null),
                SplitInput.of(2L, "100"));

        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), missing))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("no percentage");
    }

    @Test
    void reportsTheTypeItHandles() {
        assertThat(strategy.type()).isEqualTo(SplitType.PERCENTAGE);
    }
}
