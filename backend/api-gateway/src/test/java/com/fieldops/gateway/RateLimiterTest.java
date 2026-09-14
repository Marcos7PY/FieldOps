package com.fieldops.gateway;

import com.fieldops.gateway.infrastructure.ratelimit.RateLimiterProperties;
import com.fieldops.gateway.infrastructure.ratelimit.SlidingWindowRateLimiterFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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
        properties.setTrustedProxies(List.of("10.0.0.1", "127.0.0.1"));

        filter = new SlidingWindowRateLimiterFilter(properties);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldAllowRequestsWithinCapacity() {
        for (int i = 0; i < 3; i++) {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/work-orders")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 50000))
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
        for (int i = 0; i < 3; i++) {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/work-orders")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 50000))
                    .header("X-Forwarded-For", "10.0.0.50")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            filter.filter(exchange, chain).block();
        }

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/work-orders")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 50000))
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
        for (int i = 0; i < 3; i++) {
            MockServerHttpRequest reqA = MockServerHttpRequest.get("/api/v1/work-orders")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 50000))
                    .header("X-Forwarded-For", "192.168.1.1")
                    .build();
            filter.filter(MockServerWebExchange.from(reqA), chain).block();
        }

        MockServerHttpRequest reqABlocked = MockServerHttpRequest.get("/api/v1/work-orders")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 50000))
                .header("X-Forwarded-For", "192.168.1.1")
                .build();
        MockServerWebExchange exA = MockServerWebExchange.from(reqABlocked);
        filter.filter(exA, chain).block();
        assertThat(exA.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        MockServerHttpRequest reqB = MockServerHttpRequest.get("/api/v1/work-orders")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 50000))
                .header("X-Forwarded-For", "192.168.1.2")
                .build();
        MockServerWebExchange exB = MockServerWebExchange.from(reqB);
        filter.filter(exB, chain).block();
        assertThat(exB.getResponse().getStatusCode()).isNull();
        assertThat(exB.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("2");
    }

    @Test
    @DisplayName("F2-T05: IP no confiable variando X-Forwarded-For se agrupa por socket IP y la petición que excede da 429")
    void untrustedProxy_varyingXff_isThrottledByRemoteIp() {
        properties.setCapacity(100);
        properties.setTrustedProxies(List.of("10.0.0.1")); // 198.51.100.2 NO es confiable

        // 100 requests permitidos
        for (int i = 0; i < 100; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/work-orders")
                    .remoteAddress(new InetSocketAddress("198.51.100.2", 40000))
                    .header("X-Forwarded-For", "203.0.113." + i)
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
            assertThat(ex.getResponse().getStatusCode()).isNull();
        }

        // Petición 101 rechazada con 429
        MockServerHttpRequest req101 = MockServerHttpRequest.get("/api/v1/work-orders")
                .remoteAddress(new InetSocketAddress("198.51.100.2", 40000))
                .header("X-Forwarded-For", "203.0.113.250")
                .build();
        MockServerWebExchange ex101 = MockServerWebExchange.from(req101);
        filter.filter(ex101, chain).block();
        assertThat(ex101.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("F2-T05: IP confiable en trustedProxies con X-Forwarded-For distinto permite cuota por cada cliente")
    void trustedProxy_varyingXff_allowsEachClientQuota() {
        properties.setCapacity(100);
        properties.setTrustedProxies(List.of("10.0.0.1"));

        for (int i = 0; i < 101; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/api/v1/work-orders")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 50000))
                    .header("X-Forwarded-For", "203.0.113." + i)
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
            assertThat(ex.getResponse().getStatusCode()).isNull();
        }
    }

    @Test
    @DisplayName("F2-T06: evictIdleClients purga entradas antiguas vacías")
    void evictIdleClients_purgesOldEntries() {
        long oldTime = System.currentTimeMillis() - 100_000L;
        for (int i = 0; i < 1000; i++) {
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(oldTime);
            filter.getClientRequests().put("client-" + i, queue);
        }
        assertThat(filter.getClientRequests()).hasSize(1000);

        filter.evictIdleClients();

        assertThat(filter.getClientRequests()).isEmpty();
    }
}
