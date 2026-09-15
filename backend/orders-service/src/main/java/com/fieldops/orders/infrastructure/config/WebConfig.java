package com.fieldops.orders.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Las cabeceras de seguridad de las evidencias se aplican en
 * {@code WorkOrderController#getEvidenceContent}, que es el unico punto por el que
 * se sirven ficheros desde que se retiro el resource handler sobre /uploads/**.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
