package io.github.guipalm4.ledger.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** O seeder (bench/seed/seed.sql) em escala reduzida: invariantes, distribuição e determinismo. */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class SeedTests {

    static final Path SCRIPT = Path.of("..", "bench", "seed", "seed.sql");

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void install() throws Exception {
        execute(Files.readString(SCRIPT));
    }

    @Test
    void generatesAConsistentLedger() throws Exception {
        execute("CALL seed.load(0.002)");

        assertThat(count("SELECT count(*) FROM account")).isEqualTo(200);
        assertThat(count("SELECT count(*) FROM ledger_entry")).isGreaterThan(15_000);
        LedgerInvariants.assertHold(jdbc);
    }

    @Test
    void businessAccountsConcentrateTheEntries() throws Exception {
        execute("CALL seed.load(0.002)");

        long business = count("""
                SELECT count(*) FROM ledger_entry e JOIN account a ON a.id = e.account_id
                WHERE a.holder_id LIKE 'seed-b-%'""");
        long smallestBusiness = count("""
                SELECT min(n) FROM (SELECT count(*) AS n FROM ledger_entry e JOIN account a ON a.id = e.account_id
                                    WHERE a.holder_id LIKE 'seed-b-%' GROUP BY a.id) x""");
        assertThat((double) business / count("SELECT count(*) FROM ledger_entry")).isBetween(0.35, 0.65);
        assertThat(smallestBusiness).isGreaterThanOrEqualTo(4_350);
    }

    @Test
    void sameSaltGivesTheSameDataset() throws Exception {
        execute("CALL seed.load(0.001, 7)");
        var first = fingerprint();
        execute("CALL seed.load(0.001, 8)");
        var other = fingerprint();
        execute("CALL seed.load(0.001, 7)");

        assertThat(fingerprint()).isEqualTo(first).isNotEqualTo(other);
    }

    private String fingerprint() {
        return jdbc.sql("""
                SELECT md5(string_agg(e.transaction_id::text || e.account_id::text || e.amount || e.balance_after
                                      || e.description || e.counterparty_name || e.occurred_at, ',' ORDER BY e.id))
                       || md5((SELECT string_agg(id::text || holder_name || balance, ',' ORDER BY id) FROM account))
                FROM ledger_entry e""").query(String.class).single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    /** JDBC puro: o script tem $$ e casts com "::", que o parser de parâmetros nomeados não aceita. */
    private void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
