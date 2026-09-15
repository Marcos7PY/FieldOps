package com.fieldops.orders.infrastructure.outbox;

import com.fieldops.orders.infrastructure.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry registry, OutboxEventRepository repository) {
        Gauge.builder("fieldops.outbox.pending", repository,
                        OutboxEventRepository::countByPublishedAtIsNullAndDeadLetteredAtIsNull)
                .description("Eventos del outbox pendientes de publicar")
                .register(registry);

        Gauge.builder("fieldops.outbox.dead_lettered", repository,
                        OutboxEventRepository::countByDeadLetteredAtIsNotNull)
                .description("Eventos del outbox descartados tras agotar reintentos")
                .register(registry);
    }
}
