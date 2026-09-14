package com.splitwise.web.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Records money that actually changed hands. */
@Getter
@Setter
public class CreateSettlementForm {

    @NotNull(message = "Choose who paid.")
    private Long fromUser;

    @NotNull(message = "Choose who was paid.")
    private Long toUser;

    @NotNull(message = "Enter an amount.")
    @DecimalMin(value = "0.01", message = "The amount must be greater than zero.")
    @Digits(integer = 10, fraction = 2, message = "Amounts go to the cent, no further.")
    private BigDecimal amount;
}
