package com.splitwise.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Form-backing object for the new-group screen. Deliberately not the Group
 * entity: binding straight onto an entity lets a stray request parameter write
 * a field the form never meant to expose.
 */
@Getter
@Setter
public class CreateGroupForm {

    @NotBlank(message = "Give the group a name.")
    @Size(max = 100, message = "Keep the name under 100 characters.")
    private String name;

    @NotBlank(message = "Pick a currency.")
    @Pattern(regexp = "[A-Z]{3}", message = "Use a 3-letter currency code, e.g. PHP.")
    private String currency = "PHP";
}
