package io.github.guipalm4.ledger.cqrs.query;

import io.github.guipalm4.ledger.cqrs.Roles;
import io.github.guipalm4.ledger.cqrs.query.Views.AccountView;
import io.github.guipalm4.ledger.cqrs.query.Views.CategoryView;
import io.github.guipalm4.ledger.cqrs.query.Views.CounterpartyView;
import io.github.guipalm4.ledger.cqrs.query.Views.EntryView;
import io.github.guipalm4.ledger.cqrs.query.Views.MonthlyCategoryTotal;
import io.github.guipalm4.ledger.cqrs.query.Views.MonthlySummary;
import io.github.guipalm4.ledger.cqrs.query.Views.SearchResult;
import io.github.guipalm4.ledger.cqrs.query.Views.StatementPage;
import io.github.guipalm4.ledger.domain.EntryKind;
import io.github.guipalm4.ledger.domain.LedgerViolation;
import io.github.guipalm4.ledger.domain.Violation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Q1–Q4 da v2. Q1 continua no modelo de escrita ({@code account}); Q2–Q4 leem só as projeções (ADR-0011).
 * <p>
 * Q2 e Q4 percorrem os blocos da conta do mês mais recente para o mais antigo, abrem os lançamentos de cada
 * bloco e param quando a página enche. Filtros que valem para o bloco inteiro (período, palavras, contraparte)
 * descartam blocos antes de abri-los; cada lançamento é conferido de novo com a mesma regra da v1.
 * <p>
 * Conta inexistente só é verificada quando o resultado vem vazio, para não pagar uma consulta extra no caso comum.
 */
@Service
@Profile(Roles.V2)
public class AccountQueries {

    public static final int MAX_LIMIT = 200;

    /** Abre os lançamentos de um bloco: as chaves curtas estão descritas em V2__projections.sql. */
    private static final String ENTRIES = """
            SELECT b.month, e.id, e.tx, e.ts, e.kind, e.amt, e.bal, e.cat, e.dsc, e.cp, e.cpa
            FROM statement_view b
            CROSS JOIN LATERAL jsonb_to_recordset(b.entries)
                AS e(id bigint, tx uuid, ts timestamptz, kind text, amt bigint, bal bigint, cat smallint, dsc text,
                     cp text, cpa uuid)
            WHERE b.account_id = :accountId
            """;

    /**
     * A ordem pelo mês deixa o Postgres ler os blocos já ordenados pela chave primária e ordenar só dentro de
     * cada bloco (incremental sort), parando no limite. A categoria entra depois do limite, só para a página.
     */
    private static final String PAGE = """
            SELECT p.id, p.tx, p.ts, p.kind, p.amt, p.bal, c.code AS category_code, c.name AS category_name, p.dsc,
                   p.cp, p.cpa
            FROM (%s ORDER BY b.month DESC, e.ts DESC, e.id DESC LIMIT :limit) p
            JOIN category c ON c.id = p.cat
            ORDER BY p.ts DESC, p.id DESC""";

    private final JdbcClient jdbc;

    AccountQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Q1 — saldo atual. */
    public AccountView account(UUID accountId) {
        return jdbc.sql("""
                        SELECT id, holder_id, holder_name, currency, balance, overdraft_limit
                        FROM account WHERE id = :id""")
                .param("id", accountId)
                .query((rs, _) -> new AccountView(rs.getObject("id", UUID.class), rs.getString("holder_id"),
                        rs.getString("holder_name"), rs.getString("currency"), rs.getLong("balance"),
                        rs.getLong("overdraft_limit")))
                .optional()
                .orElseThrow(() -> notFound(accountId));
    }

    /** Q2 — extrato do período [from, to), do mais recente para o mais antigo, paginado por keyset. */
    public StatementPage statement(UUID accountId, Instant from, Instant to, int limit, @Nullable String cursor) {
        requireLimit(limit);
        requirePeriod(from, to);
        var sql = new StringBuilder(ENTRIES).append("""
                 AND b.month BETWEEN :fromMonth AND :toMonth AND e.ts >= :from AND e.ts < :to""");
        var params = new HashMap<String, Object>();
        params.put("accountId", accountId);
        params.put("from", utc(from));
        params.put("to", utc(to));
        params.put("fromMonth", month(from));
        params.put("toMonth", month(to));
        if (cursor != null) {
            var position = StatementCursor.decode(cursor);
            sql.append(" AND b.month <= :cursorMonth AND (e.ts, e.id) < (:cursorAt, :cursorId)");
            params.put("cursorMonth", month(position.occurredAt()));
            params.put("cursorAt", utc(position.occurredAt()));
            params.put("cursorId", position.entryId());
        }
        params.put("limit", limit + 1);

        var rows = page(sql, params);
        if (rows.isEmpty()) {
            requireAccount(accountId);
        }
        if (rows.size() <= limit) {
            return new StatementPage(accountId, rows, null);
        }
        var page = rows.subList(0, limit);
        var last = page.getLast();
        return new StatementPage(accountId, List.copyOf(page),
                new StatementCursor(last.occurredAt(), last.entryId()).encode());
    }

    /** Q3 — totais por mês e categoria nos 12 meses que terminam em {@code until} (UTC). */
    public MonthlySummary monthlySummary(UUID accountId, YearMonth until) {
        var from = until.minusMonths(11);
        var items = jdbc.sql("""
                        SELECT to_char(s.month, 'YYYY-MM') AS month, c.code, c.name, s.credits, s.debits, s.entries
                        FROM monthly_summary s JOIN category c ON c.id = s.category_id
                        WHERE s.account_id = :accountId AND s.month BETWEEN :start AND :end
                        ORDER BY s.month DESC, c.code""")
                .param("accountId", accountId)
                .param("start", from.atDay(1))
                .param("end", until.atDay(1))
                .query((rs, _) -> new MonthlyCategoryTotal(YearMonth.parse(rs.getString("month")),
                        new CategoryView(rs.getString("code"), rs.getString("name")), rs.getLong("credits"),
                        rs.getLong("debits"), rs.getLong("entries")))
                .list();
        if (items.isEmpty()) {
            requireAccount(accountId);
        }
        return new MonthlySummary(accountId, from, until, items);
    }

    /** Q4 — busca com filtros combinados, do mais recente para o mais antigo. */
    public SearchResult search(UUID accountId, SearchCriteria criteria) {
        requireLimit(criteria.limit());
        var sql = new StringBuilder(ENTRIES);
        var params = new HashMap<String, Object>();
        params.put("accountId", accountId);
        if (criteria.text() != null) {
            sql.append("""
                     AND b.search_vector @@ plainto_tsquery('simple', :text)
                     AND to_tsvector('simple', e.dsc || ' ' || e.cp) @@ plainto_tsquery('simple', :text)""");
            params.put("text", criteria.text());
        }
        if (criteria.counterparty() != null) {
            sql.append(" AND b.counterparties ILIKE :counterparty AND e.cp ILIKE :counterparty");
            params.put("counterparty", "%" + escapeLike(criteria.counterparty()) + "%");
        }
        if (criteria.category() != null) {
            sql.append(" AND e.cat = (SELECT id FROM category WHERE code = :category)");
            params.put("category", criteria.category());
        }
        if (criteria.from() != null) {
            sql.append(" AND b.month >= :fromMonth AND e.ts >= :from");
            params.put("fromMonth", month(criteria.from()));
            params.put("from", utc(criteria.from()));
        }
        if (criteria.to() != null) {
            sql.append(" AND b.month <= :toMonth AND e.ts < :to");
            params.put("toMonth", month(criteria.to()));
            params.put("to", utc(criteria.to()));
        }
        if (criteria.minAmount() != null) {
            sql.append(" AND abs(e.amt) >= :minAmount");
            params.put("minAmount", criteria.minAmount());
        }
        if (criteria.maxAmount() != null) {
            sql.append(" AND abs(e.amt) <= :maxAmount");
            params.put("maxAmount", criteria.maxAmount());
        }
        params.put("limit", criteria.limit());

        var items = page(sql, params);
        if (items.isEmpty()) {
            requireAccount(accountId);
        }
        return new SearchResult(accountId, items);
    }

    private List<EntryView> page(CharSequence filteredEntries, Map<String, Object> params) {
        return jdbc.sql(PAGE.formatted(filteredEntries)).params(params).query((rs, _) -> entry(rs)).list();
    }

    private static EntryView entry(ResultSet rs) throws SQLException {
        return new EntryView(rs.getLong("id"), rs.getObject("tx", UUID.class),
                rs.getObject("ts", OffsetDateTime.class).toInstant(), EntryKind.valueOf(rs.getString("kind")),
                rs.getLong("amt"), rs.getLong("bal"),
                new CategoryView(rs.getString("category_code"), rs.getString("category_name")),
                rs.getString("dsc"),
                new CounterpartyView(rs.getString("cp"), rs.getObject("cpa", UUID.class)));
    }

    private void requireAccount(UUID accountId) {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM account WHERE id = :id)")
                .param("id", accountId).query(Boolean.class).single();
        if (!exists) {
            throw notFound(accountId);
        }
    }

    private static LedgerViolation notFound(UUID accountId) {
        return new LedgerViolation(Violation.ACCOUNT_NOT_FOUND, "account %s not found".formatted(accountId));
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private static void requirePeriod(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    /** Primeiro dia do mês (UTC) do instante: a chave dos blocos e do resumo mensal. */
    private static LocalDate month(Instant instant) {
        return YearMonth.from(instant.atOffset(ZoneOffset.UTC)).atDay(1);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
