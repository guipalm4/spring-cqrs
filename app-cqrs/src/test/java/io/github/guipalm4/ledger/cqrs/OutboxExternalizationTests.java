package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.guipalm4.ledger.cqrs.command.CommandOutcome.Completed;
import io.github.guipalm4.ledger.cqrs.command.CommandResult.AccountOpened;
import io.github.guipalm4.ledger.cqrs.command.CommandService;
import io.github.guipalm4.ledger.cqrs.event.EntryPosted;
import io.github.guipalm4.ledger.domain.Deposit;
import io.github.guipalm4.ledger.domain.EntryKind;
import io.github.guipalm4.ledger.domain.Money;
import io.github.guipalm4.ledger.domain.OpenAccount;
import io.github.guipalm4.ledger.domain.Transfer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Na {@code command-api} da v3, cada lançamento chega ao Kafka pelo outbox do Modulith (ADR-0004, ADR-0012): um
 * evento por lançamento, com a conta como chave, e a publicação sai do registro depois de enviada (modo DELETE).
 * As projeções da v2 ficam desligadas.
 */
@SpringBootTest
@ActiveProfiles(Roles.COMMAND)
@Import({PostgresTestcontainer.class, KafkaTestcontainer.class})
class OutboxExternalizationTests {

    @Autowired
    CommandService commands;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    KafkaConnectionDetails kafka;

    @Test
    void eachEntryReachesKafkaKeyedByItsAccount() {
        var source = open("outbox-source");
        var target = open("outbox-target");

        commands.execute(UUID.randomUUID().toString(),
                new Deposit(source, Money.of(1_000, "BRL"), "SALARY", "salário", "Acme Tecnologia"));
        commands.execute(UUID.randomUUID().toString(),
                new Transfer(source, target, Money.of(300, "BRL"), "aluguel dividido"));

        var records = consume(3);
        assertThat(records).extracting(ConsumerRecord::key)
                .containsExactlyInAnyOrder(source.toString(), source.toString(), target.toString());
        var events = records.stream().map(r -> json.readValue(r.value(), EntryPosted.class)).toList();
        assertThat(events).extracting(EntryPosted::kind)
                .containsExactlyInAnyOrder(EntryKind.DEPOSIT, EntryKind.TRANSFER_OUT, EntryKind.TRANSFER_IN);
        for (var event : events) {
            var stored = jdbc.sql("""
                            SELECT account_id, amount, balance_after, occurred_at FROM ledger_entry WHERE id = :id""")
                    .param("id", event.entryId())
                    .query((rs, _) -> new Object[] {rs.getObject(1, UUID.class), rs.getLong(2), rs.getLong(3),
                            rs.getTimestamp(4).toInstant()})
                    .single();
            assertThat(new Object[] {event.accountId(), event.amount(), event.balanceAfter(), event.occurredAt()})
                    .containsExactly(stored);
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                jdbc.sql("SELECT count(*) FROM event_publication").query(Long.class).single()).isZero());
        assertThat(jdbc.sql("SELECT count(*) FROM statement_view").query(Long.class).single()).isZero();
    }

    private UUID open(String holderId) {
        var outcome = (Completed) commands.execute(UUID.randomUUID().toString(),
                new OpenAccount(holderId + "-" + UUID.randomUUID(), "Titular", Currency.getInstance("BRL"), 0));
        return ((AccountOpened) outcome.result()).accountId();
    }

    private List<ConsumerRecord<String, String>> consume(int expected) {
        var records = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(),
                new StringDeserializer())) {
            consumer.subscribe(List.of(EntryPosted.TOPIC));
            var deadline = Instant.now().plusSeconds(30);
            while (records.size() < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }
}
