package com.splitwise.split;

import java.math.BigDecimal;
import java.util.List;

public interface SplitStrategy {

    SplitType type();

    /**
     * Divides {@code total} among {@code inputs}.
     *
     * @return one share per input, in the same order, summing to exactly {@code total}
     * @throws InvalidSplitException if the inputs are unusable or the shares
     *                               would not sum to the total
     */
    List<ShareDto> split(BigDecimal total, List<SplitInput> inputs);
}
