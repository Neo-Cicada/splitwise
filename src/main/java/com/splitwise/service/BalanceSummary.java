package com.splitwise.service;

import java.math.BigDecimal;

/**
 * One user's headline position, shaped for the template.
 *
 * @param net positive when the group owes the user, negative when they owe it
 */
public record BalanceSummary(Long userId, BigDecimal net) {

    public boolean isOwed() {
        return net.signum() > 0;
    }

    public boolean isOwing() {
        return net.signum() < 0;
    }

    public boolean isSettled() {
        return net.signum() == 0;
    }

    /** Magnitude, so templates never render a minus sign next to "You owe". */
    public BigDecimal getAbsolute() {
        return net.abs();
    }
}
