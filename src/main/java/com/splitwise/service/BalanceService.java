package com.splitwise.service;

import com.splitwise.repository.BalanceRepository;
import com.splitwise.repository.BalanceView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Derives balances. Nothing here is ever persisted: every figure is recomputed
 * from expenses, expense_shares and settlements on each call.
 */
@Service
@Transactional(readOnly = true)
public class BalanceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final BalanceRepository balanceRepository;
    private final AccessGuard accessGuard;

    public BalanceService(BalanceRepository balanceRepository, AccessGuard accessGuard) {
        this.balanceRepository = balanceRepository;
        this.accessGuard = accessGuard;
    }

    /**
     * Every member's net position, largest creditor first.
     *
     * <p>The totals must sum to zero: every peso one member is owed is a peso
     * another member owes. A non-zero sum means the derivation is wrong, so it
     * fails loudly here rather than quietly showing wrong numbers.
     */
    public List<BalanceView> balancesFor(Long groupId, Long actorId) {
        accessGuard.requireActiveMember(groupId, actorId);
        List<BalanceView> balances = balanceRepository.findBalances(groupId);

        BigDecimal sum = balances.stream()
                .map(BalanceService::netOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException(
                    "Balances for group " + groupId + " sum to " + sum.toPlainString()
                            + " instead of zero.");
        }
        return balances;
    }

    /** The given user's own position, for the headline on the detail page. */
    public BalanceSummary summaryFor(Long groupId, Long userId) {
        BigDecimal net = balancesFor(groupId, userId).stream()
                .filter(balance -> userId.equals(balance.getUserId()))
                .map(BalanceService::netOf)
                .findFirst()
                .orElse(ZERO);

        return new BalanceSummary(userId, net);
    }

    /** A null net would mean the LEFT JOINs leaked; treat it as zero and move on. */
    private static BigDecimal netOf(BalanceView balance) {
        BigDecimal net = balance.getNetBalance();
        return net == null ? ZERO : net;
    }
}
