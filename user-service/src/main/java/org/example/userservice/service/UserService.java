package org.example.userservice.service;

import org.example.userservice.dto.LoginRequest;
import org.example.userservice.dto.RegisterRequest;
import org.example.userservice.entity.User;
import org.example.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void registerUser(RegisterRequest request) {
        // 1. Check if email already exists
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalStateException("Email is already registered.");
        }

        // 2. Map DTO to Entity and Hash the Password
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(request.role());

        String hashed = passwordEncoder.encode(request.password());
        user.setPasswordHash(hashed);

        // 3. Save to Database
        userRepository.save(user);
    }

    public User verifyLogin(LoginRequest request) {
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
}