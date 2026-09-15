package com.fieldops.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityGatewayTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void shouldReturnUnauthorizedWhenNoTokenProvidedForSecuredRoute() {
        webTestClient.get()
                .uri("/api/v1/work-orders")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.title").isEqualTo("Unauthorized");
    }

    @Test
    void shouldPermitLoginEndpointWithoutAuthentication() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().value(status -> {
                    // Gateway routes to auth-service (which is down during unit test, producing 503 or 500, but NOT 401)
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
                });
    }

    @Test
    void shouldPermitJwksEndpointWithoutAuthentication() {
        webTestClient.get()
                .uri("/.well-known/jwks.json")
                .exchange()
                .expectStatus().value(status -> {
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
                });
    }

    @Test
    void shouldAuthenticateValidJwtToken() {
        Jwt jwt = new Jwt(
                "token-abc",
                Instant.now(),
                Instant.now().plusSeconds(900),
                Map.of("alg", "RS256"),
                Map.of("sub", "supervisor", "userId", 1L, "roles", List.of("ROLE_SUPERVISOR"))
        );

        when(reactiveJwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        webTestClient.get()
                .uri("/api/v1/work-orders")
                .header("Authorization", "Bearer token-abc")
                .exchange()
                .expectStatus().value(status -> {
                    // Token is validated; request proceeds to backend route (fails with 503/connection refused because orders-service is not running, but NOT 401)
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
                });
    }
}
