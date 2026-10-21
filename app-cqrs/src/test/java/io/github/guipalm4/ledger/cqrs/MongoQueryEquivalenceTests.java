package io.github.guipalm4.ledger.cqrs;

import static org.awaitility.Awaitility.await;

import io.github.guipalm4.ledger.cqrs.command.CommandService;
import io.github.guipalm4.ledger.cqrs.event.EntryPosted;
import io.github.guipalm4.ledger.cqrs.projection.MongoProjections;
import io.github.guipalm4.ledger.cqrs.query.AccountQueries;
import io.github.guipalm4.ledger.domain.Deposit;
import io.github.guipalm4.ledger.domain.EntryKind;
import io.github.guipalm4.ledger.domain.Money;
import io.github.guipalm4.ledger.domain.Transfer;
import io.github.guipalm4.ledger.domain.Withdraw;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * As consultas da v3 sobre o Mongo respondem Q2–Q4 exatamente como a v1 sobre o ledger (contrato idêntico, ADR-0008
 * e ADR-0012), com o dataset do seed em escala reduzida e depois de novos comandos, que chegam pelo Kafka.
 * <p>
 * O dataset entra no Mongo pela própria projeção, em paralelo e sem ordem, e não pela exportação do {@code make seed}.
 */
// Os três papéis num processo só: os profiles da query-api e do projetor desligam o Flyway, que é da command-api.
@SpringBootTest(properties = "spring.flyway.enabled=true")
@ActiveProfiles({Roles.COMMAND, Roles.QUERY, Roles.PROJECTOR})
@Import({PostgresTestcontainer.class, KafkaTestcontainer.class, MongoTestcontainer.class})
class MongoQueryEquivalenceTests {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    MongoProjections projections;

    @Autowired
    AccountQueries queries;

    @Autowired
    CommandService commands;

    @Test
    void mongoAnswersLikeTheLedger() throws Exception {
        execute(Files.readString(ProjectionEquivalenceTests.SEED));
        execute("CALL seed.load(0.002)");
        jdbc.sql("""
                        SELECT id, transaction_id, account_id, occurred_at, kind, amount, balance_after, category_id,
                               description, counterparty_name, counterparty_account_id
                        FROM ledger_entry""")
                .query((rs, _) -> new EntryPosted(rs.getLong("id"), rs.getObject("transaction_id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        EntryKind.valueOf(rs.getString("kind")), rs.getLong("amount"), rs.getLong("balance_after"),
                        rs.getShort("category_id"), rs.getString("description"), rs.getString("counterparty_name"),
                        rs.getObject("counterparty_account_id", UUID.class)))
                .list()
                .parallelStream()
                .forEach(projections::apply);
        var business = account("seed-b-0000001");
        var retail = account("seed-r-0000100");

        var v1 = new V1Reference(jdbc);
        v1.assertSameAnswers(queries, business);
        v1.assertSameAnswers(queries, retail);

        for (int i = 0; i < 30; i++) {
            commands.execute(UUID.randomUUID().toString(),
                    new Deposit(business, brl(1_000 + i), "SALARY", "salário " + i, "Acme Tecnologia"));
            commands.execute(UUID.randomUUID().toString(),
                    new Withdraw(business, brl(100 + i), "GROCERIES", "feira de domingo", "Mercado Central " + i));
            commands.execute(UUID.randomUUID().toString(),
                    new Transfer(business, retail, brl(10 + i), "Aluguel dividido"));
        }
        long entries = jdbc.sql("SELECT count(*) FROM ledger_entry").query(Long.class).single();
        await().atMost(Duration.ofSeconds(30)).until(() -> projectedEntries() == entries);
        v1.assertSameAnswers(queries, business);
        v1.assertSameAnswers(queries, retail);
    }

    private long projectedEntries() {
        long total = 0;
        for (var summary : mongo.getCollection(MongoProjections.SUMMARIES).find()
                .projection(new Document("entries", 1))) {
            total += ((Number) summary.get("entries")).longValue();
        }
        return total;
    }

    private UUID account(String holderId) {
        return jdbc.sql("SELECT id FROM account WHERE holder_id = :holderId").param("holderId", holderId)
                .query(UUID.class).single();
    }

    private static Money brl(long amount) {
        return Money.of(amount, "BRL");
    }

    /** JDBC puro: o script tem $$ e casts com "::", que o parser de parâmetros nomeados não aceita. */
    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
