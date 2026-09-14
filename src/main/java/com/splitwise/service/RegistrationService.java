package com.splitwise.service;

import com.splitwise.domain.User;
import com.splitwise.repository.UserRepository;
import com.splitwise.support.FieldValidationException;
import com.splitwise.web.form.RegisterForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterForm form) {
        String email = form.getEmail().trim();

        // The unique index on users.email is the real guard; this check exists
        // to turn the collision into a readable field error first.
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new FieldValidationException("email", "That email is already registered.");
        }

        return userRepository.save(User.builder()
                .name(form.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(form.getPassword()))
                .build());
    }
}
