package org.example.userservice.controller;

import org.example.userservice.dto.LoginRequest;
import org.example.userservice.dto.LoginResponse;
import org.example.userservice.dto.RegisterRequest;
import org.example.userservice.entity.User;
import org.example.userservice.service.JwtService;
import org.example.userservice.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        try {
            userService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            // 1. Verify credentials and get the user
            User user = userService.verifyLogin(request);

            // 2. Generate the token
            String token = jwtService.generateToken(user);

            // 3. Return the token in the response wrapper
            return ResponseEntity.ok(new LoginResponse(token));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }
}