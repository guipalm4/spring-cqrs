package io.github.guipalm4.ledger.cqrs.event;

import io.github.guipalm4.ledger.cqrs.Roles;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code ledger.outbox.backlog}: publicações gravadas e ainda não enviadas ao Kafka. No modo {@code DELETE}, é o
 * tamanho da tabela (ADR-0012). Uma consulta por coleta do Prometheus (5 s), no pool dos comandos.
 */
@Component
@Profile(Roles.COMMAND)
class OutboxMetrics {

    OutboxMetrics(JdbcClient jdbc, MeterRegistry registry) {
        Gauge.builder("ledger.outbox.backlog",
                        () -> jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single())
                .description("Publicações do outbox ainda não enviadas ao Kafka")
                .register(registry);
    }
}
