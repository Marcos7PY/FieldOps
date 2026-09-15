package com.fieldops.orders;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    protected static final MSSQLServerContainer<?> SQL_SERVER_CONTAINER;

    static {
        SQL_SERVER_CONTAINER = new MSSQLServerContainer<>(
                "mcr.microsoft.com/mssql/server:2022-CU13-ubuntu-22.04")
                .acceptLicense();
        SQL_SERVER_CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER_CONTAINER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }
}
