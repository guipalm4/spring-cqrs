package io.github.guipalm4.ledger.cqrs.projection;

import io.github.guipalm4.ledger.cqrs.Roles;
import io.github.guipalm4.ledger.cqrs.event.EntryPosted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * O projetor da v3 (ADR-0004, ADR-0012): consome {@code ledger.entry-posted} no consumer group
 * {@code projector-mongo} e aplica cada lançamento no Mongo. O offset só é confirmado depois das duas escritas; se o
 * processo cair entre elas, o Kafka reentrega, e as duas são idempotentes.
 * <p>
 * {@code ledger.projection.lag} é o atraso da projeção (H5): do {@code occurred_at} do lançamento, que é o início da
 * transação do comando, até as duas escritas terminarem. Superestima o atraso em até a duração do comando.
 * Reentregas não entram no timer, só no contador de duplicatas.
 */
@Component
@Profile(Roles.PROJECTOR)
class Projector implements ApplicationRunner {

    static final String GROUP = "projector-mongo";

    private final MongoProjections projections;
    private final Timer lag;
    private final Counter duplicates;

    Projector(MongoProjections projections, MeterRegistry registry) {
        this.projections = projections;
        this.lag = Timer.builder("ledger.projection.lag")
                .description("Do occurred_at do lançamento até ele estar nas projeções do Mongo")
                .publishPercentileHistogram()
                .maximumExpectedValue(Duration.ofMinutes(5))
                .register(registry);
        this.duplicates = Counter.builder("ledger.projection.duplicates")
                .description("Lançamentos reentregues que já estavam na projeção")
                .register(registry);
    }

    @Override
    public void run(ApplicationArguments args) {
        projections.createIndexes();
    }

    @KafkaListener(id = GROUP, groupId = GROUP, topics = EntryPosted.TOPIC,
            concurrency = "${ledger.projector.concurrency}")
    void on(EntryPosted event) {
        if (projections.apply(event)) {
            lag.record(Duration.between(event.occurredAt(), Instant.now()));
        } else {
            duplicates.increment();
        }
    }
}
