package com.splitwise.split;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EqualSplitStrategyTest {

    private final EqualSplitStrategy strategy = new EqualSplitStrategy();

    private static List<SplitInput> participants(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> SplitInput.of((long) i))
                .toList();
    }

    private static BigDecimal sum(List<ShareDto> shares) {
        return shares.stream().map(ShareDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("100.00 across 3 gives 33.34 / 33.33 / 33.33 and sums to exactly 100.00")
    void splitsOneHundredAcrossThree() {
        List<ShareDto> shares = strategy.split(new BigDecimal("100.00"), participants(3));

        assertThat(shares).extracting(ShareDto::amount)
                .containsExactly(
                        new BigDecimal("33.34"),
                        new BigDecimal("33.33"),
                        new BigDecimal("33.33"));
        assertThat(sum(shares)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("0.01 across 3 does not crash and still sums to 0.01")
    void splitsASingleCentAcrossThree() {
        List<ShareDto> shares = strategy.split(new BigDecimal("0.01"), participants(3));

        assertThat(shares).extracting(ShareDto::amount)
                .containsExactly(
                        new BigDecimal("0.01"),
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"));
        assertThat(sum(shares)).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("10.00 across 4 divides evenly with no remainder")
    void splitsEvenly() {
        List<ShareDto> shares = strategy.split(new BigDecimal("10.00"), participants(4));

        assertThat(shares).extracting(ShareDto::amount)
                .containsOnly(new BigDecimal("2.50"));
        assertThat(sum(shares)).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("every share stays within one cent of every other")
    void sharesStayWithinOneCentOfEachOther() {
        List<ShareDto> shares = strategy.split(new BigDecimal("100.00"), participants(7));

        BigDecimal max = shares.stream().map(ShareDto::amount).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal min = shares.stream().map(ShareDto::amount).min(BigDecimal::compareTo).orElseThrow();

        assertThat(max.subtract(min)).isLessThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(sum(shares)).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsAnEmptyParticipantList() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("10.00"), List.of()))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("at least one participant");
    }

    @Test
    void rejectsADuplicateParticipant() {
        List<SplitInput> duplicated = List.of(SplitInput.of(1L), SplitInput.of(1L));

        assertThatThrownBy(() -> strategy.split(new BigDecimal("10.00"), duplicated))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void rejectsANonPositiveTotal() {
        assertThatThrownBy(() -> strategy.split(new BigDecimal("0.00"), participants(2)))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void reportsTheTypeItHandles() {
        assertThat(strategy.type()).isEqualTo(SplitType.EQUAL);
    }
}
