package com.fieldops.notifications.infrastructure.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Component
public class UserDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(UserDirectoryClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public record UserDto(Long id, String username, String fullName, String email, List<String> roles) {}

    public UserDirectoryClient(
            RestClient.Builder restClientBuilder,
            @Value("${fieldops.auth-service.url:http://localhost:8081}") String authServiceUrl,
            @Value("${fieldops.security.internal-token:}") String internalToken
    ) {
        this.restClient = restClientBuilder.baseUrl(authServiceUrl).build();
        this.internalToken = internalToken;
    }

    @Cacheable(value = "users", key = "#userId", unless = "#result == null")
    public Optional<UserDto> findUserById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            UserDto user = restClient.get()
                    .uri("/api/v1/users/{id}", userId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(UserDto.class);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            log.warn("Failed to fetch user {} from auth-service: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
