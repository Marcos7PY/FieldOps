package com.fieldops.auth.infrastructure.config;

import java.util.List;

public record UserPrincipal(
        Long userId,
        String username,
        List<String> roles
) {}