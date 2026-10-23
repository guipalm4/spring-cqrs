package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guipalm4.ledger.cqrs.event.EntryPosted;
import io.github.guipalm4.ledger.cqrs.projection.MongoProjections;
import io.github.guipalm4.ledger.domain.EntryKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * A projeção do Mongo aplica cada lançamento uma vez só, chegue ele fora de ordem ou repetido (ADR-0012). Sem Kafka:
 * os eventos entram direto na projeção.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@ActiveProfiles(Roles.PROJECTOR)
@Import(MongoTestcontainer.class)
class MongoProjectionsTests {

    static final short SALARY = 1;
    static final short GROCERIES = 2;

    @Autowired
    MongoProjections projections;

    @Autowired
    MongoTemplate mongo;

    @Test
    void appliesEachEntryOnceWhateverTheOrder() {
        projections.createIndexes();
        var account = UUID.randomUUID();
        var first = entry(account, 1, "2026-09-30T23:59:59.999999Z", 1_000, SALARY);
        var second = entry(account, 2, "2026-10-01T00:00:00.000001Z", -300, GROCERIES);
        var third = entry(account, 3, "2026-10-02T12:00:00Z", -200, GROCERIES);

        // O terceiro chega antes do segundo, e os dois do mês de outubro chegam de novo.
        assertThat(List.of(projections.apply(first), projections.apply(third), projections.apply(second),
                projections.apply(third), projections.apply(second))).containsExactly(true, true, true, false, false);

        assertThat(entryIds(account, "2026-09")).containsExactly(1L);
        assertThat(entryIds(account, "2026-10")).containsExactlyInAnyOrder(2L, 3L);
        var october = summary(account, "2026-10", GROCERIES);
        assertThat(List.of(october.getLong("credits"), october.getLong("debits"), (long) october.getInteger("entries")))
                .containsExactly(0L, 500L, 2L);
        assertThat(summary(account, "2026-09", SALARY).getLong("credits")).isEqualTo(1_000);
        assertThat(entryOf(account, "2026-09").getLong("ts")).isEqualTo(
                MongoProjections.micros(Instant.parse("2026-09-30T23:59:59.999999Z")));
    }

    private List<Long> entryIds(UUID account, String month) {
        return block(account, month).getList("entries", Document.class).stream().map(e -> e.getLong("id")).toList();
    }

    private Document entryOf(UUID account, String month) {
        return block(account, month).getList("entries", Document.class).getFirst();
    }

    private Document block(UUID account, String month) {
        return mongo.getCollection(MongoProjections.STATEMENTS)
                .find(new Document("account", account).append("month", month)).first();
    }

    private Document summary(UUID account, String month, short category) {
        return mongo.getCollection(MongoProjections.SUMMARIES)
                .find(new Document("account", account).append("month", month).append("cat", (int) category)).first();
    }

    private static EntryPosted entry(UUID account, long id, String occurredAt, long amount, short category) {
        var kind = amount > 0 ? EntryKind.DEPOSIT : EntryKind.WITHDRAWAL;
        return new EntryPosted(id, UUID.randomUUID(), account, Instant.parse(occurredAt), kind, amount, 0, category,
                "lançamento " + id, "Contraparte " + id, null);
    }
}
