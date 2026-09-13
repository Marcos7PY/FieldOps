package com.fieldops.gateway;

import com.fieldops.gateway.infrastructure.tracing.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdFilterTest {

    private TraceIdFilter filter;
    private GatewayFilterChain chain;
    private AtomicReference<ServerWebExchange> capturedExchange;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter(null);
        capturedExchange = new AtomicReference<>();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });
    }

    @Test
    void shouldGenerateTraceIdWhenHeaderIsMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/work-orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseTraceId = exchange.getResponse().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(responseTraceId).isNotNull().isNotBlank();

        ServerWebExchange downstreamExchange = capturedExchange.get();
        assertThat(downstreamExchange).isNotNull();
        String downstreamTraceId = downstreamExchange.getRequest().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(downstreamTraceId).isEqualTo(responseTraceId);
    }

    @Test
    void shouldPropagateExistingTraceId() {
        String existingTraceId = "trace-fixed-abcdef123456";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/work-orders")
                .header(TraceIdFilter.TRACE_ID_HEADER, existingTraceId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseTraceId = exchange.getResponse().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(responseTraceId).isEqualTo(existingTraceId);

        ServerWebExchange downstreamExchange = capturedExchange.get();
        assertThat(downstreamExchange).isNotNull();
        String downstreamTraceId = downstreamExchange.getRequest().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(downstreamTraceId).isEqualTo(existingTraceId);
    }
}
