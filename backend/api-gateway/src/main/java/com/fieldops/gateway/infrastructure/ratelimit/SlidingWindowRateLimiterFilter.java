package com.fieldops.gateway.infrastructure.ratelimit;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlidingWindowRateLimiterFilter implements GlobalFilter, Ordered {

    private final RateLimiterProperties properties;
    private final Map<String, Deque<Long>> clientRequests = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiterFilter(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return -10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
                .map(p -> "user:" + p.getName())
                .defaultIfEmpty("ip:" + resolveClientKey(exchange.getRequest()))
                .flatMap(key -> applyLimit(key, exchange, chain));
    }

    private Mono<Void> applyLimit(String clientKey, ServerWebExchange exchange, GatewayFilterChain chain) {
        // Techo duro contra fuga de memoria / DoS
        if (clientRequests.size() >= properties.getMaxTrackedClients() && !clientRequests.containsKey(clientKey)) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return response.setComplete();
        }

        long now = System.currentTimeMillis();
        long windowMs = properties.getWindowSeconds() * 1000L;
        int capacity = properties.getCapacity();

        Deque<Long> timestamps = clientRequests.computeIfAbsent(clientKey, k -> new ArrayDeque<>());

        boolean allowed;
        int remaining;
        long retryAfterSeconds;

        synchronized (timestamps) {
            long cutoff = now - windowMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }

            int count = timestamps.size();
            if (count >= capacity) {
                allowed = false;
                remaining = 0;
                long oldest = timestamps.peekFirst() != null ? timestamps.peekFirst() : now;
                retryAfterSeconds = Math.max(1, (oldest + windowMs - now) / 1000);
            } else {
                allowed = true;
                timestamps.addLast(now);
                remaining = capacity - (count + 1);
                retryAfterSeconds = 0;
            }
        }

        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(capacity));
        headers.add("X-RateLimit-Remaining", String.valueOf(remaining));

        if (!allowed) {
            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);

            String body = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded. Try again in "
                    + retryAfterSeconds + " seconds.\"}";
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }

        return chain.filter(exchange);
    }

    private String resolveClientKey(ServerHttpRequest request) {
        String remoteIp = null;
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            remoteIp = remote.getAddress().getHostAddress();
        }

        if (remoteIp != null && properties.getTrustedProxies().contains(remoteIp)) {
            String xff = request.getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return remoteIp != null ? remoteIp : "anonymous";
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictIdleClients() {
        long cutoff = System.currentTimeMillis() - properties.getWindowSeconds() * 1000L;
        clientRequests.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
    }

    public Map<String, Deque<Long>> getClientRequests() {
        return clientRequests;
    }

    public void reset() {
        clientRequests.clear();
    }
}
