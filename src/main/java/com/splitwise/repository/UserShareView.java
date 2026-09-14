package com.splitwise.repository;

import java.math.BigDecimal;

/** One user's share of one expense, for feed rendering. */
public record UserShareView(Long expenseId, BigDecimal amount) {
}
