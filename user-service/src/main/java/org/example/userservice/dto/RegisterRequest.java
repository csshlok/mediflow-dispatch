package org.example.userservice.dto;

import org.example.userservice.enums.Role;

public record RegisterRequest(
        String name,
        String email,
        String password,
        Role role
) {}