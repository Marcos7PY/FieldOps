package com.fieldops.orders.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${fieldops.uploads-path:uploads}")
    private String uploadsPath;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                applySecurityHeaders(response);
                return true;
            }
        }).addPathPatterns("/uploads/**");
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> uploadsSecurityHeadersFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                if (request.getRequestURI().startsWith("/uploads/")) {
                    applySecurityHeaders(response);
                }
                filterChain.doFilter(request, response);
            }
        });
        registration.addUrlPatterns("/uploads/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        return registration;
    }

    private static void applySecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment");
        response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
        response.setHeader("Cache-Control", "private, max-age=3600");
    }
}
