package com.splitwise.web.form;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One row of the participant table on the expense form.
 *
 * <p>{@code value} carries whatever the chosen split type needs: the exact
 * amount for EXACT, the percentage for PERCENTAGE, and nothing at all for
 * EQUAL. One row shape, three readings.
 */
@Getter
@Setter
@NoArgsConstructor
public class ParticipantInput {

    private Long userId;
    private boolean selected;
    private BigDecimal value;

    public ParticipantInput(Long userId) {
        this.userId = userId;
    }
}
