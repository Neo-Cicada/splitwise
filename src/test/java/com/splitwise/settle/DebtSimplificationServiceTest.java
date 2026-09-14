package com.splitwise.settle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtSimplificationServiceTest {

    private final DebtSimplificationService service = new DebtSimplificationService();

    private static MemberBalance balance(long id, String name, String net) {
        return new MemberBalance(id, name, new BigDecimal(net));
    }

    /** Applies every suggested transfer and returns the resulting balances. */
    private static Map<Long, BigDecimal> applyAll(List<MemberBalance> start,
                                                  List<SuggestedTransfer> transfers) {
        Map<Long, BigDecimal> net = new HashMap<>();
        start.forEach(b -> net.put(b.userId(), b.net()));
        // Paying down a debt raises the payer's balance and lowers the payee's,
        // exactly as the settlements table feeds into the balance query.
        transfers.forEach(t -> {
            net.merge(t.fromUserId(), t.amount(), BigDecimal::add);
            net.merge(t.toUserId(), t.amount().negate(), BigDecimal::add);
        });
        return net;
    }

    private static void assertAllSettled(Map<Long, BigDecimal> net) {
        net.forEach((userId, amount) ->
                assertThat(amount)
                        .as("user %d should be settled", userId)
                        .isEqualByComparingTo("0.00"));
    }

    @Test
    @DisplayName("applying every suggested transfer brings all balances to zero")
    void applyingEverySuggestionSettlesEveryone() {
        List<MemberBalance> balances = List.of(
                balance(1, "Ana", "450.00"),
                balance(2, "Ben", "-300.00"),
                balance(3, "Carla", "-200.00"),
                balance(4, "Dan", "50.00"));

        List<SuggestedTransfer> transfers = service.simplify(balances);

        assertAllSettled(applyAll(balances, transfers));
        assertThat(transfers).hasSizeLessThanOrEqualTo(balances.size() - 1);
    }

    @Test
    @DisplayName("stays within n-1 transfers on a messy spread")
    void staysWithinTheBound() {
        List<MemberBalance> balances = List.of(
                balance(1, "A", "1163.22"),
                balance(2, "B", "-50.14"),
                balance(3, "C", "-185.95"),
                balance(4, "D", "-927.13"));

        List<SuggestedTransfer> transfers = service.simplify(balances);

        assertThat(transfers).hasSizeLessThanOrEqualTo(3);
        assertAllSettled(applyAll(balances, transfers));
    }

    @Test
    @DisplayName("the largest debtor is matched against the largest creditor first")
    void greedyPairsLargestFirst() {
        List<MemberBalance> balances = List.of(
                balance(1, "Small creditor", "100.00"),
                balance(2, "Big creditor", "500.00"),
                balance(3, "Big debtor", "-450.00"),
                balance(4, "Small debtor", "-150.00"));

        List<SuggestedTransfer> transfers = service.simplify(balances);

        assertThat(transfers.get(0).fromName()).isEqualTo("Big debtor");
        assertThat(transfers.get(0).toName()).isEqualTo("Big creditor");
        assertThat(transfers.get(0).amount()).isEqualByComparingTo("450.00");
        assertAllSettled(applyAll(balances, transfers));
    }

    @Test
    @DisplayName("an exactly matched pair needs a single transfer")
    void exactPairNeedsOneTransfer() {
        List<MemberBalance> balances = List.of(
                balance(1, "Ana", "75.00"),
                balance(2, "Ben", "-75.00"));

        List<SuggestedTransfer> transfers = service.simplify(balances);

        assertThat(transfers).hasSize(1);
        assertThat(transfers.get(0).amount()).isEqualByComparingTo("75.00");
        assertAllSettled(applyAll(balances, transfers));
    }

    @Test
    @DisplayName("a settled group needs no transfers at all")
    void settledGroupNeedsNothing() {
        List<MemberBalance> balances = List.of(
                balance(1, "Ana", "0.00"),
                balance(2, "Ben", "0.00"));

        assertThat(service.simplify(balances)).isEmpty();
    }

    @Test
    @DisplayName("cent-level remainders still settle exactly")
    void centRemaindersSettleExactly() {
        List<MemberBalance> balances = List.of(
                balance(1, "Ana", "33.34"),
                balance(2, "Ben", "-33.33"),
                balance(3, "Carla", "-0.01"));

        List<SuggestedTransfer> transfers = service.simplify(balances);

        assertAllSettled(applyAll(balances, transfers));
        assertThat(transfers).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void refusesBalancesThatDoNotSumToZero() {
        List<MemberBalance> broken = List.of(
                balance(1, "Ana", "100.00"),
                balance(2, "Ben", "-90.00"));

        assertThatThrownBy(() -> service.simplify(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10.00");
    }

    @Test
    @DisplayName("suggestions are stable across repeated calls")
    void suggestionsAreStable() {
        List<MemberBalance> balances = new ArrayList<>(List.of(
                balance(1, "A", "200.00"),
                balance(2, "B", "-100.00"),
                balance(3, "C", "-100.00")));

        assertThat(service.simplify(balances))
                .isEqualTo(service.simplify(balances));
    }
}
