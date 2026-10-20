package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guipalm4.ledger.cqrs.command.CommandService;
import io.github.guipalm4.ledger.cqrs.query.AccountQueries;
import io.github.guipalm4.ledger.cqrs.query.SearchCriteria;
import io.github.guipalm4.ledger.cqrs.query.Views.CategoryView;
import io.github.guipalm4.ledger.cqrs.query.Views.CounterpartyView;
import io.github.guipalm4.ledger.cqrs.query.Views.EntryView;
import io.github.guipalm4.ledger.cqrs.query.Views.MonthlyCategoryTotal;
import io.github.guipalm4.ledger.domain.Deposit;
import io.github.guipalm4.ledger.domain.EntryKind;
import io.github.guipalm4.ledger.domain.Money;
import io.github.guipalm4.ledger.domain.Transfer;
import io.github.guipalm4.ledger.domain.Withdraw;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * As projeções respondem Q2–Q4 exatamente como a v1 responderia sobre o ledger (contrato idêntico, ADR-0008).
 * A referência é o SQL da v1 ({@code AccountQueries} da app-baseline) rodado sobre a mesma {@code ledger_entry},
 * com o dataset do seed em escala reduzida, depois de reconstruir as projeções e depois de novos comandos.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class ProjectionEquivalenceTests {

    static final Path SEED = Path.of("..", "bench", "seed", "seed.sql");
    static final String V1_ENTRY_COLUMNS = """
            e.id, e.transaction_id, e.occurred_at, e.kind, e.amount, e.balance_after, c.code AS category_code,
            c.name AS category_name, e.description, e.counterparty_name, e.counterparty_account_id""";

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

        assertSameAnswers(business);
        assertSameAnswers(retail);

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
        assertSameAnswers(business);
        assertSameAnswers(retail);

        // Atualizar um a um e reconstruir do zero dão os mesmos blocos e totais.
        var incremental = snapshot();
        execute("CALL rebuild_projections()");
        assertThat(snapshot()).isEqualTo(incremental);
    }

    private void assertSameAnswers(UUID account) {
        for (var month : List.of(YearMonth.of(2024, 10), YearMonth.of(2025, 6), YearMonth.of(2026, 9),
                YearMonth.now(ZoneOffset.UTC))) {
            var from = month.minusMonths(2).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            var to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            assertThat(allPages(account, from, to)).as("Q2 %s", month).isEqualTo(v1Statement(account, from, to));
            assertThat(queries.monthlySummary(account, month).items()).as("Q3 %s", month)
                    .isEqualTo(v1MonthlySummary(account, month));
        }
        var since = Instant.parse("2025-07-01T00:00:00Z");
        var until = Instant.parse("2026-01-15T12:00:00Z");
        for (var criteria : List.of(
                search("supermercado", null, null, null, null, null, null, 50),
                search("sushi kaze", null, null, null, null, null, null, 50),
                search("domingo", null, null, null, null, null, null, 200),
                search(null, "nonna", null, null, null, null, null, 50),
                search(null, "central 1", null, null, null, null, null, 200),
                search("pix", null, "TRANSFER", since, null, null, null, 50),
                search(null, null, "GROCERIES", since, until, 10_000L, 50_000L, 200),
                search("salário", "acme", "SALARY", null, null, null, null, 20),
                search("palavra-inexistente", null, null, null, null, null, null, 50))) {
            assertThat(queries.search(account, criteria).items()).as("Q4 %s", criteria)
                    .isEqualTo(v1Search(account, criteria));
        }
    }

    private List<EntryView> allPages(UUID account, Instant from, Instant to) {
        var all = new ArrayList<EntryView>();
        String cursor = null;
        do {
            var page = queries.statement(account, from, to, 50, cursor);
            all.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);
        return all;
    }

    private List<EntryView> v1Statement(UUID account, Instant from, Instant to) {
        return jdbc.sql("SELECT " + V1_ENTRY_COLUMNS + """
                         FROM ledger_entry e JOIN category c ON c.id = e.category_id
                        WHERE e.account_id = :accountId AND e.occurred_at >= :from AND e.occurred_at < :to
                        ORDER BY e.occurred_at DESC, e.id DESC""")
                .param("accountId", account).param("from", utc(from)).param("to", utc(to))
                .query((rs, _) -> v1Entry(rs)).list();
    }

    private List<MonthlyCategoryTotal> v1MonthlySummary(UUID account, YearMonth until) {
        return jdbc.sql("""
                        SELECT to_char(date_trunc('month', e.occurred_at AT TIME ZONE 'UTC'), 'YYYY-MM') AS month,
                               c.code, c.name,
                               coalesce(sum(e.amount) FILTER (WHERE e.amount > 0), 0) AS credits,
                               coalesce(-sum(e.amount) FILTER (WHERE e.amount < 0), 0) AS debits,
                               count(*) AS entries
                        FROM ledger_entry e JOIN category c ON c.id = e.category_id
                        WHERE e.account_id = :accountId AND e.occurred_at >= :start AND e.occurred_at < :end
                        GROUP BY 1, c.code, c.name
                        ORDER BY 1 DESC, c.code""")
                .param("accountId", account)
                .param("start", until.minusMonths(11).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC))
                .param("end", until.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC))
                .query((rs, _) -> new MonthlyCategoryTotal(YearMonth.parse(rs.getString("month")),
                        new CategoryView(rs.getString("code"), rs.getString("name")), rs.getLong("credits"),
                        rs.getLong("debits"), rs.getLong("entries")))
                .list();
    }

    private List<EntryView> v1Search(UUID account, SearchCriteria criteria) {
        var sql = new StringBuilder("SELECT " + V1_ENTRY_COLUMNS + """
                 FROM ledger_entry e JOIN category c ON c.id = e.category_id
                WHERE e.account_id = :accountId""");
        var params = new HashMap<String, Object>();
        params.put("accountId", account);
        if (criteria.text() != null) {
            sql.append(" AND to_tsvector('simple', e.description || ' ' || e.counterparty_name)"
                    + " @@ plainto_tsquery('simple', :text)");
            params.put("text", criteria.text());
        }
        if (criteria.counterparty() != null) {
            sql.append(" AND e.counterparty_name ILIKE :counterparty");
            params.put("counterparty", "%" + criteria.counterparty() + "%");
        }
        if (criteria.category() != null) {
            sql.append(" AND c.code = :category");
            params.put("category", criteria.category());
        }
        if (criteria.from() != null) {
            sql.append(" AND e.occurred_at >= :from");
            params.put("from", utc(criteria.from()));
        }
        if (criteria.to() != null) {
            sql.append(" AND e.occurred_at < :to");
            params.put("to", utc(criteria.to()));
        }
        if (criteria.minAmount() != null) {
            sql.append(" AND abs(e.amount) >= :minAmount");
            params.put("minAmount", criteria.minAmount());
        }
        if (criteria.maxAmount() != null) {
            sql.append(" AND abs(e.amount) <= :maxAmount");
            params.put("maxAmount", criteria.maxAmount());
        }
        sql.append(" ORDER BY e.occurred_at DESC, e.id DESC LIMIT :limit");
        params.put("limit", criteria.limit());
        return jdbc.sql(sql.toString()).params(params).query((rs, _) -> v1Entry(rs)).list();
    }

    private static EntryView v1Entry(java.sql.ResultSet rs) throws SQLException {
        return new EntryView(rs.getLong("id"), rs.getObject("transaction_id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(), EntryKind.valueOf(rs.getString("kind")),
                rs.getLong("amount"), rs.getLong("balance_after"),
                new CategoryView(rs.getString("category_code"), rs.getString("category_name")),
                rs.getString("description"),
                new CounterpartyView(rs.getString("counterparty_name"),
                        rs.getObject("counterparty_account_id", UUID.class)));
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

    private static SearchCriteria search(@Nullable String text, @Nullable String counterparty,
            @Nullable String category, @Nullable Instant from, @Nullable Instant to, @Nullable Long minAmount,
            @Nullable Long maxAmount, int limit) {
        return new SearchCriteria(text, counterparty, category, from, to, minAmount, maxAmount, limit);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
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
