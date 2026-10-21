package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guipalm4.ledger.cqrs.command.CommandService;
import io.github.guipalm4.ledger.cqrs.query.AccountQueries;
import io.github.guipalm4.ledger.domain.Deposit;
import io.github.guipalm4.ledger.domain.Money;
import io.github.guipalm4.ledger.domain.Transfer;
import io.github.guipalm4.ledger.domain.Withdraw;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * As projeções respondem Q2–Q4 exatamente como a v1 responderia sobre o ledger (contrato idêntico, ADR-0008).
 * A referência é o SQL da v1 ({@link V1Reference}) rodado sobre a mesma {@code ledger_entry}, com o dataset do seed
 * em escala reduzida, depois de reconstruir as projeções e depois de novos comandos.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class ProjectionEquivalenceTests {

    static final Path SEED = Path.of("..", "bench", "seed", "seed.sql");

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountQueries queries;

    @Autowired
    CommandService commands;

    @Test
    void projectionsAnswerLikeTheLedger() throws Exception {
        execute(Files.readString(SEED));
        execute("CALL seed.load(0.002)");
        execute("CALL rebuild_projections()");
        LedgerInvariants.assertHold(jdbc);
        var business = account("seed-b-0000001");
        var retail = account("seed-r-0000100");

        var v1 = new V1Reference(jdbc);
        v1.assertSameAnswers(queries, business);
        v1.assertSameAnswers(queries, retail);

        // Depois do rebuild, os comandos atualizam as projeções um lançamento por vez.
        for (int i = 0; i < 30; i++) {
            commands.execute(UUID.randomUUID().toString(),
                    new Deposit(business, brl(1_000 + i), "SALARY", "salário " + i, "Acme Tecnologia"));
            commands.execute(UUID.randomUUID().toString(),
                    new Withdraw(business, brl(100 + i), "GROCERIES", "feira de domingo", "Mercado Central " + i));
            commands.execute(UUID.randomUUID().toString(),
                    new Transfer(business, retail, brl(10 + i), "Aluguel dividido"));
        }
        LedgerInvariants.assertHold(jdbc);
        v1.assertSameAnswers(queries, business);
        v1.assertSameAnswers(queries, retail);

        // Atualizar um a um e reconstruir do zero dão os mesmos blocos e totais.
        var incremental = snapshot();
        execute("CALL rebuild_projections()");
        assertThat(snapshot()).isEqualTo(incremental);
    }

    private List<String> snapshot() {
        return jdbc.sql("""
                SELECT account_id || ' ' || month || ' ' || md5(CAST(entries AS text)) || ' ' || md5(CAST(search_vector AS text))
                FROM statement_view
                UNION ALL
                SELECT account_id || ' ' || month || ' ' || category_id || ' ' || credits || ' ' || debits || ' ' || entries
                FROM monthly_summary
                ORDER BY 1""").query(String.class).list();
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
