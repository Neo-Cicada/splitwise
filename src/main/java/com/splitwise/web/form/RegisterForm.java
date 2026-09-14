package com.splitwise.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterForm {

    @NotBlank(message = "Enter your name.")
    @Size(max = 100, message = "Keep the name under 100 characters.")
    private String name;

    @NotBlank(message = "Enter an email address.")
    @Email(message = "That does not look like an email address.")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Choose a password.")
    @Size(min = 8, max = 72, message = "Use at least 8 characters.")
    private String password;

    @NotBlank(message = "Confirm your password.")
    private String confirmPassword;

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}
