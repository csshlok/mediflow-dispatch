package org.example.userservice.dto;

import org.example.userservice.enums.Role;

import java.util.UUID;

public record RegisterRequest(
        String name,
        String email,
        String password,
        Role role,
        UUID ambulanceId // PARAMEDIC only: the ambulance this crew member works on
) {}
