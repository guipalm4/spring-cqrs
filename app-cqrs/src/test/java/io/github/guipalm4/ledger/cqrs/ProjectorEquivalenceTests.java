package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.guipalm4.ledger.cqrs.command.CommandOutcome.Completed;
import io.github.guipalm4.ledger.cqrs.command.CommandResult.AccountOpened;
import io.github.guipalm4.ledger.cqrs.command.CommandService;
import io.github.guipalm4.ledger.cqrs.projection.MongoProjections;
import io.github.guipalm4.ledger.domain.Deposit;
import io.github.guipalm4.ledger.domain.Money;
import io.github.guipalm4.ledger.domain.OpenAccount;
import io.github.guipalm4.ledger.domain.Transfer;
import io.github.guipalm4.ledger.domain.Withdraw;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comando → outbox → Kafka → projetor → Mongo dá as mesmas projeções que a v2 (ADR-0012): depois dos comandos, o
 * {@code rebuild_projections()} da v2 reconstrói as tabelas no Postgres a partir do mesmo ledger, e os documentos do
 * Mongo têm de ser iguais a elas, lançamento por lançamento e total por total.
 */
// Os dois papéis num processo só: o profile do projetor desliga o Flyway, que na v3 é da command-api.
@SpringBootTest(properties = "spring.flyway.enabled=true")
@ActiveProfiles({Roles.COMMAND, Roles.PROJECTOR})
@Import({PostgresTestcontainer.class, KafkaTestcontainer.class, MongoTestcontainer.class})
class ProjectorEquivalenceTests {

    @Autowired
    CommandService commands;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MongoTemplate mongo;

    @Test
    void mongoProjectionsMatchTheV2Rebuild() {
        var business = open("projector-business");
        var retail = open("projector-retail");
        for (int i = 0; i < 20; i++) {
            commands.execute(UUID.randomUUID().toString(),
                    new Deposit(business, brl(1_000 + i), "SALARY", "salário " + i, "Acme Tecnologia"));
            commands.execute(UUID.randomUUID().toString(),
                    new Withdraw(business, brl(100 + i), "GROCERIES", "feira de domingo", "Mercado Central " + i));
            commands.execute(UUID.randomUUID().toString(),
                    new Transfer(business, retail, brl(10 + i), "Aluguel dividido"));
        }

        long entries = jdbc.sql("SELECT count(*) FROM ledger_entry").query(Long.class).single();
        await().atMost(Duration.ofSeconds(30)).until(() -> mongoStatements().size() == entries);
        jdbc.sql("CALL rebuild_projections()").update();

        assertThat(mongoStatements()).containsExactlyInAnyOrderElementsOf(v2Statements());
        assertThat(mongoSummaries()).containsExactlyInAnyOrderElementsOf(v2Summaries());
    }

    /** Um lançamento por linha, com a conta e o mês do bloco em que está. */
    private List<String> mongoStatements() {
        var rows = new ArrayList<String>();
        for (var block : mongo.getCollection(MongoProjections.STATEMENTS).find()) {
            for (var e : block.getList("entries", Document.class)) {
                rows.add(String.join("|", block.get("account", UUID.class).toString(), block.getString("month"),
                        String.valueOf(e.getLong("id")), e.get("tx", UUID.class).toString(),
                        String.valueOf(e.getLong("ts")), e.getString("kind"), String.valueOf(e.getLong("amt")),
                        String.valueOf(e.getLong("bal")), String.valueOf(e.getInteger("cat")), e.getString("dsc"),
                        e.getString("cp"), String.valueOf(e.get("cpa", UUID.class))));
            }
        }
        return rows;
    }

    private List<String> v2Statements() {
        return jdbc.sql("""
                        SELECT v.account_id, to_char(v.month, 'YYYY-MM') AS month, e.*
                        FROM statement_view v
                        CROSS JOIN LATERAL jsonb_to_recordset(v.entries)
                            AS e(id bigint, tx uuid, ts text, kind text, amt bigint, bal bigint, cat smallint,
                                 dsc text, cp text, cpa uuid)""")
                .query((rs, _) -> String.join("|", rs.getString("account_id"), rs.getString("month"),
                        rs.getString("id"), rs.getString("tx"),
                        String.valueOf(MongoProjections.micros(Instant.parse(rs.getString("ts")))),
                        rs.getString("kind"), rs.getString("amt"), rs.getString("bal"), rs.getString("cat"),
                        rs.getString("dsc"), rs.getString("cp"), String.valueOf(rs.getString("cpa"))))
                .list();
    }

    private List<String> mongoSummaries() {
        var rows = new ArrayList<String>();
        for (var s : mongo.getCollection(MongoProjections.SUMMARIES).find()) {
            rows.add(String.join("|", s.get("account", UUID.class).toString(), s.getString("month"),
                    String.valueOf(s.getInteger("cat")), String.valueOf(s.getLong("credits")),
                    String.valueOf(s.getLong("debits")), String.valueOf(s.getInteger("entries"))));
        }
        return rows;
    }

    private List<String> v2Summaries() {
        return jdbc.sql("""
                        SELECT account_id, to_char(month, 'YYYY-MM'), category_id, credits, debits, entries
                        FROM monthly_summary""")
                .query((rs, _) -> String.join("|", rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)))
                .list();
    }

    private UUID open(String holderId) {
        var outcome = (Completed) commands.execute(UUID.randomUUID().toString(),
                new OpenAccount(holderId + "-" + UUID.randomUUID(), "Titular", Currency.getInstance("BRL"), 0));
        return ((AccountOpened) outcome.result()).accountId();
    }

    private static Money brl(long amount) {
        return Money.of(amount, "BRL");
    }
}
