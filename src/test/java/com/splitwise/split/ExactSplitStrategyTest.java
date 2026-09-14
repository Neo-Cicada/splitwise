package com.splitwise.split;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExactSplitStrategyTest {

    private final ExactSplitStrategy strategy = new ExactSplitStrategy();

    @Test
    @DisplayName("accepts amounts that sum to the total")
    void acceptsMatchingAmounts() {
        List<ShareDto> shares = strategy.split(new BigDecimal("100.00"), List.of(
                SplitInput.of(1L, "60.00"),
                SplitInput.of(2L, "39.99"),
                SplitInput.of(3L, "0.01")));

        assertThat(shares).extracting(ShareDto::amount)
                .containsExactly(
                        new BigDecimal("60.00"),
                        new BigDecimal("39.99"),
                        new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("rejects amounts that do not sum to the total")
    void rejectsAmountsThatDoNotSumToTheTotal() {
        List<SplitInput> short_ = List.of(
                SplitInput.of(1L, "50.00"),
                SplitInput.of(2L, "49.99"));

        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), short_))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("99.99")
                .hasMessageContaining("100.00");
    }

    @Test
    @DisplayName("rejects amounts that overshoot the total")
    void rejectsAmountsThatOvershoot() {
        List<SplitInput> over = List.of(
                SplitInput.of(1L, "50.00"),
                SplitInput.of(2L, "50.01"));

        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), over))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void rejectsAMissingAmount() {
        List<SplitInput> missing = List.of(
                new SplitInput(1L, null),
                SplitInput.of(2L, "100.00"));

        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), missing))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsANegativeAmount() {
        List<SplitInput> negative = List.of(
                SplitInput.of(1L, "-10.00"),
                SplitInput.of(2L, "110.00"));

        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), negative))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("rejects sub-cent precision rather than silently rounding it")
    void rejectsSubCentPrecision() {
        List<SplitInput> tooPrecise = List.of(
                SplitInput.of(1L, "33.333"),
                SplitInput.of(2L, "66.667"));

        assertThatThrownBy(() -> strategy.split(new BigDecimal("100.00"), tooPrecise))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("cents");
    }

    @Test
    void reportsTheTypeItHandles() {
        assertThat(strategy.type()).isEqualTo(SplitType.EXACT);
    }
}
