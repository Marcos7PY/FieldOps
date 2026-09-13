package com.fieldops.gateway;

import com.fieldops.gateway.infrastructure.ratelimit.RateLimiterProperties;
import com.fieldops.gateway.infrastructure.ratelimit.SlidingWindowRateLimiterFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterTest {

    private RateLimiterProperties properties;
    private SlidingWindowRateLimiterFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setEnabled(true);
        properties.setCapacity(3);
        properties.setWindowSeconds(5);

        filter = new SlidingWindowRateLimiterFilter(properties);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldAllowRequestsWithinCapacity() {
        for (int i = 0; i < 3; i++) {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/work-orders")
                    .header("X-Forwarded-For", "192.168.1.100")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
            assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("3");
            assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo(String.valueOf(2 - i));
        }
    }

    @Test
    void shouldRejectRequestsExceedingCapacityWith429() {
        // First 3 requests are allowed
        for (int i = 0; i < 3; i++) {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/work-orders")
                    .header("X-Forwarded-For", "10.0.0.50")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            filter.filter(exchange, chain).block();
        }

        // 4th request must be rejected with 429
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/work-orders")
                .header("X-Forwarded-For", "10.0.0.50")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("3");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void shouldIsolateLimitsByClientIp() {
        // Client A consumes full capacity
        for (int i = 0; i < 3; i++) {
            MockServerHttpRequest reqA = MockServerHttpRequest.get("/api/v1/work-orders")
                    .header("X-Forwarded-For", "192.168.1.1")
                    .build();
            filter.filter(MockServerWebExchange.from(reqA), chain).block();
        }

        // Client A is blocked
        MockServerHttpRequest reqABlocked = MockServerHttpRequest.get("/api/v1/work-orders")
                .header("X-Forwarded-For", "192.168.1.1")
                .build();
        MockServerWebExchange exA = MockServerWebExchange.from(reqABlocked);
        filter.filter(exA, chain).block();
        assertThat(exA.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Client B is still allowed
        MockServerHttpRequest reqB = MockServerHttpRequest.get("/api/v1/work-orders")
                .header("X-Forwarded-For", "192.168.1.2")
                .build();
        MockServerWebExchange exB = MockServerWebExchange.from(reqB);
        filter.filter(exB, chain).block();
        assertThat(exB.getResponse().getStatusCode()).isNull();
        assertThat(exB.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("2");
    }
}
