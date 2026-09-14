package com.splitwise.web;

import com.splitwise.service.RegistrationService;
import com.splitwise.support.FieldValidationException;
import com.splitwise.web.form.RegisterForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        Model model) {
        if (error != null) {
            model.addAttribute("loginError", "That email and password do not match an account.");
        }
        if (logout != null) {
            model.addAttribute("loginNotice", "You have been signed out.");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (!form.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "The passwords do not match.");
        }
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            registrationService.register(form);
        } catch (FieldValidationException e) {
            bindingResult.rejectValue(e.getField(), "invalid", e.getMessage());
            return "auth/register";
        }

        redirectAttributes.addFlashAttribute("success", "Account created. Sign in to continue.");
        return "redirect:/login";
    }
}
