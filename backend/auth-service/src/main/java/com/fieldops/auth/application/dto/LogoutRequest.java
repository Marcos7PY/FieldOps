package com.fieldops.auth.application.dto;

public record LogoutRequest(
        String refreshToken
) {}