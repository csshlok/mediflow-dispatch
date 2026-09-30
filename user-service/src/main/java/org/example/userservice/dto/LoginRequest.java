package org.example.userservice.dto;

public record LoginRequest(
        String email,
        String password
) {}