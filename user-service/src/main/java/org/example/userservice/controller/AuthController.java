package org.example.userservice.controller;

import org.example.userservice.dto.LoginRequest;
import org.example.userservice.dto.LoginResponse;
import org.example.userservice.dto.RegisterRequest;
import org.example.userservice.dto.UserSummary;
import org.example.userservice.entity.User;
import org.example.userservice.enums.Role;
import org.example.userservice.service.JwtService;
import org.example.userservice.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    public record AmbulanceAssignment(UUID ambulanceId) {}

    // Only an authenticated ADMIN may create accounts, so nobody can self-assign a privileged role
    @PostMapping("/register")
    public ResponseEntity<String> register(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody RegisterRequest request) {

        ResponseEntity<String> denied = requireAdmin(authHeader);
        if (denied != null) {
            return denied;
        }

        try {
            userService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        ResponseEntity<String> denied = requireAdmin(authHeader);
        if (denied != null) {
            return denied;
        }
        List<UserSummary> users = userService.listUsers();
        return ResponseEntity.ok(users);
    }

    // Binds a paramedic to the ambulance they crew; send {"ambulanceId": null} to unassign
    @PutMapping("/users/{id}/ambulance")
    public ResponseEntity<?> assignAmbulance(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable Long id,
            @RequestBody AmbulanceAssignment assignment) {

        ResponseEntity<String> denied = requireAdmin(authHeader);
        if (denied != null) {
            return denied;
        }

        try {
            return ResponseEntity.ok(userService.assignAmbulance(id, assignment == null ? null : assignment.ambulanceId()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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

    // Returns an error response unless the header carries a valid ADMIN token
    private ResponseEntity<String> requireAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Admin token required");
        }

        Role callerRole;
        try {
            callerRole = jwtService.extractRole(authHeader.substring(7));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired token");
        }

        if (callerRole != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only admins can manage users");
        }
        return null;
    }
}
