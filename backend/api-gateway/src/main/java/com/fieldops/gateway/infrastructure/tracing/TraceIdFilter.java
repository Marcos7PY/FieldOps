package com.fieldops.gateway.infrastructure.tracing;

import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private final Tracer tracer;

    public TraceIdFilter(@Autowired(required = false) Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            if (tracer != null && tracer.currentSpan() != null) {
                traceId = tracer.currentSpan().context().traceId();
            } else {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
        }

        final String currentTraceId = traceId;
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, currentTraceId)
                .build();

        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, currentTraceId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .contextWrite(context -> context.put("traceId", currentTraceId));
    }
}
