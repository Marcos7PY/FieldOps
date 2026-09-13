package com.fieldops.orders;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MSSQLServerContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @MockBean
    protected JwtDecoder jwtDecoder;

    protected static final MSSQLServerContainer<?> SQL_SERVER_CONTAINER;

    static {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
        }

        if (dockerAvailable) {
            SQL_SERVER_CONTAINER = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense();
            SQL_SERVER_CONTAINER.start();
        } else {
            SQL_SERVER_CONTAINER = null;
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (SQL_SERVER_CONTAINER != null && SQL_SERVER_CONTAINER.isRunning()) {
            registry.add("spring.datasource.url", SQL_SERVER_CONTAINER::getJdbcUrl);
            registry.add("spring.datasource.username", SQL_SERVER_CONTAINER::getUsername);
            registry.add("spring.datasource.password", SQL_SERVER_CONTAINER::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        }
    }
}
