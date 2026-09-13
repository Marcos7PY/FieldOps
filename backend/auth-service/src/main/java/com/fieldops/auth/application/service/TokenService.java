package com.fieldops.auth.application.service;

import com.fieldops.auth.domain.model.RefreshToken;
import com.fieldops.auth.domain.model.User;

public interface TokenService {
    String generateAccessToken(User user);
    String createRefreshToken(User user);
    RefreshToken verifyAndGetRefreshToken(String rawRefreshToken);
    void revokeRefreshToken(String rawRefreshToken);
}