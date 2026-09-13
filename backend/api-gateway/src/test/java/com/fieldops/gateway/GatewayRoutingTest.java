package com.fieldops.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayRoutingTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void shouldLoadAllServiceRoutes() {
        List<String> routeIds = routeLocator.getRoutes()
                .map(Route::getId)
                .collectList()
                .block();

        assertThat(routeIds).contains(
                "auth-service-api",
                "auth-service-jwks",
                "orders-service-work-orders",
                "orders-service-clients",
                "analytics-service"
        );
    }
}
