package com.splitwise.settle;

import java.math.BigDecimal;

/** "Ben pays Ana ₱300.00" — a proposal, never recorded automatically. */
public record SuggestedTransfer(Long fromUserId, String fromName,
                                Long toUserId, String toName,
                                BigDecimal amount) {
}
