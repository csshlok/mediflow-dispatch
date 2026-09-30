package org.example.userservice.dto;

import org.example.userservice.entity.User;
import org.example.userservice.enums.Role;

import java.util.UUID;

public record UserSummary(Long id, String name, String email, Role role, UUID ambulanceId) {

    public static UserSummary of(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getAmbulanceId());
    }
}
