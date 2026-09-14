package com.fieldops.auth.application.dto;

import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        List<String> roles
) {
    public UserResponse(Long id, String username, String fullName, List<String> roles) {
        this(id, username, fullName, null, roles);
    }
}
