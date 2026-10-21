package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guipalm4.ledger.cqrs.event.EntryPosted;
import io.github.guipalm4.ledger.cqrs.projection.MongoProjections;
import io.github.guipalm4.ledger.domain.EntryKind;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import javax.sql.DataSource;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * A carga inicial do {@code make seed} da v3 ({@code MongoSeed}) grava os mesmos documentos que o projetor gravaria
 * aplicando os lançamentos um a um, inclusive nos tipos do BSON (ADR-0012).
 */
// O Flyway é da command-api; aqui ele cria o schema para o seed.
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.kafka.listener.auto-startup=false",
        "ledger.seed-mongo=true"})
@ActiveProfiles(Roles.PROJECTOR)
@Import({PostgresTestcontainer.class, MongoTestcontainer.class})
class MongoSeedTests {

    static final JsonWriterSettings TYPED = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    MongoProjections projections;

    @Autowired
    ApplicationRunner mongoSeed;

    @Test
    void bulkLoadEqualsApplyingEachEntry() throws Exception {
        execute(Files.readString(ProjectionEquivalenceTests.SEED));
        execute("CALL seed.load(0.001)");

        mongoSeed.run(null);
        var seeded = snapshot();

        projections.drop();
        projections.createIndexes();
        entries().parallelStream().forEach(projections::apply);
        assertThat(seeded).hasSizeGreaterThan(100).isEqualTo(snapshot());
    }

    /**
     * Cada documento em Extended JSON (com os tipos), sem o {@code _id}, com as chaves e os arrays em ordem fixa: o
     * upsert grava os campos do filtro numa ordem própria, que não muda nenhuma consulta.
     */
    private List<String> snapshot() {
        var rows = new ArrayList<String>();
        for (var block : mongo.getCollection(MongoProjections.STATEMENTS).find()) {
            block.remove("_id");
            block.put("entries", block.getList("entries", Document.class).stream()
                    .sorted(Comparator.comparingLong(e -> e.getLong("id"))).toList());
            block.put("cps", block.getList("cps", String.class).stream().sorted().toList());
            rows.add(new Document(new TreeMap<>(block)).toJson(TYPED));
        }
        for (var summary : mongo.getCollection(MongoProjections.SUMMARIES).find()) {
            summary.remove("_id");
            summary.put("applied", summary.getList("applied", Long.class).stream().sorted().toList());
            rows.add(new Document(new TreeMap<>(summary)).toJson(TYPED));
        }
        rows.sort(null);
        return rows;
    }

    private List<EntryPosted> entries() {
        return jdbc.sql("""
                        SELECT id, transaction_id, account_id, occurred_at, kind, amount, balance_after, category_id,
                               description, counterparty_name, counterparty_account_id
                        FROM ledger_entry""")
                .query((rs, _) -> new EntryPosted(rs.getLong("id"), rs.getObject("transaction_id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        EntryKind.valueOf(rs.getString("kind")), rs.getLong("amount"), rs.getLong("balance_after"),
                        rs.getShort("category_id"), rs.getString("description"), rs.getString("counterparty_name"),
                        rs.getObject("counterparty_account_id", UUID.class)))
                .list();
    }

    /** JDBC puro: o script tem $$ e casts com "::", que o parser de parâmetros nomeados não aceita. */
    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
