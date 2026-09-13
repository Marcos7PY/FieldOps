package com.fieldops.orders.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FieldOps Orders API")
                        .version("v1.0")
                        .description("API REST para la gestión de órdenes de trabajo, evidencias y clientes"));
    }
}