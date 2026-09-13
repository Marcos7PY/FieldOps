package com.fieldops.auth.application.dto;

import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        List<String> roles
) {}