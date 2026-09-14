package com.splitwise.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddMemberForm {

    @NotBlank(message = "Enter an email address.")
    @Email(message = "That does not look like an email address.")
    @Size(max = 255)
    private String email;
}
