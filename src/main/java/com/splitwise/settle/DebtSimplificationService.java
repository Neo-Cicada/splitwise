package com.splitwise.settle;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Proposes a short list of payments that would clear every debt in a group.
 *
 * <p>The approach is greedy: repeatedly match the largest debtor against the
 * largest creditor and move the smaller of the two amounts. Each pass zeroes
 * at least one person, so n members need at most n−1 transfers.
 *
 * <p>This is <em>not</em> provably minimal. Finding the fewest possible
 * transactions is NP-hard (it subsumes subset-sum: any group of members whose
 * balances happen to cancel among themselves could settle internally, and
 * spotting every such subset is the hard part). The n−1 bound is the practical
 * answer real apps ship, and it is what this does.
 */
@Service
public class DebtSimplificationService {

    /** A remaining obligation, held as a positive magnitude. */
    private record Node(Long userId, String name, BigDecimal amount) {
    }

    public List<SuggestedTransfer> simplify(List<MemberBalance> balances) {
        BigDecimal sum = balances.stream()
                .map(MemberBalance::net)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException(
                    "Cannot simplify debts that sum to " + sum.toPlainString() + " instead of zero.");
        }

        // Largest magnitude first; user id breaks ties so the suggestions are
        // stable between page loads rather than shuffling on every render.
        Comparator<Node> largestFirst = Comparator
                .comparing(Node::amount).reversed()
                .thenComparing(Node::userId);

        PriorityQueue<Node> debtors = new PriorityQueue<>(largestFirst);
        PriorityQueue<Node> creditors = new PriorityQueue<>(largestFirst);

        for (MemberBalance balance : balances) {
            int sign = balance.net().signum();
            if (sign < 0) {
                debtors.add(new Node(balance.userId(), balance.name(), balance.net().negate()));
            } else if (sign > 0) {
                creditors.add(new Node(balance.userId(), balance.name(), balance.net()));
            }
        }

        List<SuggestedTransfer> transfers = new ArrayList<>();
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            Node debtor = debtors.poll();
            Node creditor = creditors.poll();

            BigDecimal amount = debtor.amount().min(creditor.amount());
            transfers.add(new SuggestedTransfer(
                    debtor.userId(), debtor.name(),
                    creditor.userId(), creditor.name(),
                    amount));

            BigDecimal debtorLeft = debtor.amount().subtract(amount);
            BigDecimal creditorLeft = creditor.amount().subtract(amount);

            // At least one of these is now zero, which is what bounds the
            // number of transfers at n−1.
            if (debtorLeft.signum() > 0) {
                debtors.add(new Node(debtor.userId(), debtor.name(), debtorLeft));
            }
            if (creditorLeft.signum() > 0) {
                creditors.add(new Node(creditor.userId(), creditor.name(), creditorLeft));
            }
        }

        return transfers;
    }
}
