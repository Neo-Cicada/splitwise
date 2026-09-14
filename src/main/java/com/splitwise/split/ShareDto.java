package com.splitwise.split;

import java.math.BigDecimal;

/** One participant's resolved share, always scaled to 2 decimal places. */
public record ShareDto(Long userId, BigDecimal amount) {
}
