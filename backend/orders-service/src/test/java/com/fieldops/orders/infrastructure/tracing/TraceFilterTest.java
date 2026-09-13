package com.fieldops.orders.infrastructure.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TraceFilterTest {

    private TraceFilter filter;
    private FilterChain filterChain;
    private AtomicReference<String> mdcTraceIdDuringChain;

    @BeforeEach
    void setUp() {
        filter = new TraceFilter(null);
        filterChain = mock(FilterChain.class);
        mdcTraceIdDuringChain = new AtomicReference<>();
    }

    @Test
    void shouldGenerateTraceIdWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            mdcTraceIdDuringChain.set(MDC.get("traceId"));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        String traceId = response.getHeader(TraceFilter.TRACE_ID_HEADER);
        assertThat(traceId).isNotNull().isNotBlank();
        assertThat(mdcTraceIdDuringChain.get()).isEqualTo(traceId);
        // Ensure MDC is cleared after request
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void shouldPropagateExistingTraceId() throws ServletException, IOException {
        String existingTraceId = "incoming-trace-id-998877";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceFilter.TRACE_ID_HEADER, existingTraceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            mdcTraceIdDuringChain.set(MDC.get("traceId"));
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        String traceId = response.getHeader(TraceFilter.TRACE_ID_HEADER);
        assertThat(traceId).isEqualTo(existingTraceId);
        assertThat(mdcTraceIdDuringChain.get()).isEqualTo(existingTraceId);
        assertThat(MDC.get("traceId")).isNull();
    }
}
