package org.example.userservice.service;

import org.example.userservice.dto.LoginRequest;
import org.example.userservice.dto.RegisterRequest;
import org.example.userservice.entity.User;
import org.example.userservice.enums.Role;
import org.example.userservice.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void registerUser(RegisterRequest request) {
        // 1. Reject incomplete requests before touching the database
        if (request == null || isBlank(request.name()) || isBlank(request.email()) || request.role() == null) {
            throw new IllegalArgumentException("name, email, password and role are required");
        }
        if (request.password() == null || request.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        // 2. Check if email already exists
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalStateException("Email is already registered.");
        }

        // 3. Map DTO to Entity and Hash the Password
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        // 4. Save to Database (the unique constraint catches concurrent registrations of the same email)
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Email is already registered.");
        }
    }

    // Creates the first administrator; used only by the startup bootstrap
    public boolean createAdminIfNoneExists(String name, String email, String rawPassword) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return false;
        }
        registerUser(new RegisterRequest(name, email, rawPassword, Role.ADMIN));
        return true;
    }

    public User verifyLogin(LoginRequest request) {
        if (request == null || request.email() == null || request.password() == null) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        // 1. Look up user by email
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        // 2. Use BCrypt to compare the raw password against the stored hash
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        // 3. Return the user so the controller can pass it to the JwtService
        return user;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
