package com.splitwise.web.form;

import com.splitwise.split.SplitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreateExpenseForm {

    @NotNull(message = "Choose who paid.")
    private Long paidBy;

    @NotNull(message = "Enter an amount.")
    @DecimalMin(value = "0.01", message = "The amount must be greater than zero.")
    @Digits(integer = 10, fraction = 2, message = "Amounts go to the cent, no further.")
    private BigDecimal amount;

    @Size(max = 255, message = "Keep the description under 255 characters.")
    private String description;

    @NotNull(message = "Pick a date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate spentAt = LocalDate.now();

    @NotNull(message = "Choose how to split this.")
    private SplitType splitType = SplitType.EQUAL;

    /**
     * One entry per active group member, in the order the form renders them.
     * Spring grows this list as it binds participants[0], participants[1], ...
     */
    private List<ParticipantInput> participants = new ArrayList<>();
}
