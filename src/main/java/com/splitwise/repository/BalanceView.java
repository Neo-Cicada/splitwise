package com.splitwise.repository;

import java.math.BigDecimal;

/**
 * One member's derived position in a group.
 *
 * <p>Positive means the group owes them; negative means they owe the group.
 * Never stored — always recomputed from expenses, shares and settlements.
 */
public interface BalanceView {

    Long getUserId();

    String getName();

    BigDecimal getNetBalance();
}
