package com.splitwise.settle;

import java.math.BigDecimal;

/**
 * Input to debt simplification: one member's net position.
 *
 * @param net positive when the group owes them, negative when they owe it
 */
public record MemberBalance(Long userId, String name, BigDecimal net) {
}
