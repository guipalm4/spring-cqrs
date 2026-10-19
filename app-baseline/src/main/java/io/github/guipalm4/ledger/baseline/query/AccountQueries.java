package io.github.guipalm4.ledger.baseline.query;

import io.github.guipalm4.ledger.baseline.query.Views.AccountView;
import io.github.guipalm4.ledger.baseline.query.Views.CategoryView;
import io.github.guipalm4.ledger.baseline.query.Views.CounterpartyView;
import io.github.guipalm4.ledger.baseline.query.Views.EntryView;
import io.github.guipalm4.ledger.baseline.query.Views.MonthlyCategoryTotal;
import io.github.guipalm4.ledger.baseline.query.Views.MonthlySummary;
import io.github.guipalm4.ledger.baseline.query.Views.SearchResult;
import io.github.guipalm4.ledger.baseline.query.Views.StatementPage;
import io.github.guipalm4.ledger.domain.EntryKind;
import io.github.guipalm4.ledger.domain.LedgerViolation;
import io.github.guipalm4.ledger.domain.Violation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Q1–Q4 da v1: consultas direto sobre {@code account} e {@code ledger_entry}, o mesmo modelo da escrita.
 * <p>
 * Conta inexistente só é verificada quando o resultado vem vazio, para não pagar uma consulta extra no caso comum.
 */
@Service
public class AccountQueries {

    public static final int MAX_LIMIT = 200;

    private static final String ENTRY_COLUMNS = """
            e.id, e.transaction_id, e.occurred_at, e.kind, e.amount, e.balance_after, c.code AS category_code,
            c.name AS category_name, e.description, e.counterparty_name, e.counterparty_account_id""";

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
        var sql = new StringBuilder("SELECT " + ENTRY_COLUMNS + """
                 FROM ledger_entry e JOIN category c ON c.id = e.category_id
                WHERE e.account_id = :accountId AND e.occurred_at >= :from AND e.occurred_at < :to""");
        var params = new HashMap<String, Object>();
        params.put("accountId", accountId);
        params.put("from", utc(from));
        params.put("to", utc(to));
        if (cursor != null) {
            var position = StatementCursor.decode(cursor);
            sql.append(" AND (e.occurred_at, e.id) < (:cursorAt, :cursorId)");
            params.put("cursorAt", utc(position.occurredAt()));
            params.put("cursorId", position.entryId());
        }
        sql.append(" ORDER BY e.occurred_at DESC, e.id DESC LIMIT :limit");
        params.put("limit", limit + 1);

        var rows = jdbc.sql(sql.toString()).params(params).query((rs, _) -> entry(rs)).list();
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
                        SELECT to_char(date_trunc('month', e.occurred_at AT TIME ZONE 'UTC'), 'YYYY-MM') AS month,
                               c.code, c.name,
                               coalesce(sum(e.amount) FILTER (WHERE e.amount > 0), 0) AS credits,
                               coalesce(-sum(e.amount) FILTER (WHERE e.amount < 0), 0) AS debits,
                               count(*) AS entries
                        FROM ledger_entry e JOIN category c ON c.id = e.category_id
                        WHERE e.account_id = :accountId AND e.occurred_at >= :start AND e.occurred_at < :end
                        GROUP BY 1, c.code, c.name
                        ORDER BY 1 DESC, c.code""")
                .param("accountId", accountId)
                .param("start", from.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC))
                .param("end", until.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC))
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
        var sql = new StringBuilder("SELECT " + ENTRY_COLUMNS + """
                 FROM ledger_entry e JOIN category c ON c.id = e.category_id
                WHERE e.account_id = :accountId""");
        var params = new HashMap<String, Object>();
        params.put("accountId", accountId);
        if (criteria.text() != null) {
            sql.append(" AND e.search_vector @@ plainto_tsquery('simple', :text)");
            params.put("text", criteria.text());
        }
        if (criteria.counterparty() != null) {
            sql.append(" AND e.counterparty_name ILIKE :counterparty");
            params.put("counterparty", "%" + escapeLike(criteria.counterparty()) + "%");
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

        var items = jdbc.sql(sql.toString()).params(params).query((rs, _) -> entry(rs)).list();
        if (items.isEmpty()) {
            requireAccount(accountId);
        }
        return new SearchResult(accountId, items);
    }

    private static EntryView entry(ResultSet rs) throws SQLException {
        return new EntryView(rs.getLong("id"), rs.getObject("transaction_id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(), EntryKind.valueOf(rs.getString("kind")),
                rs.getLong("amount"), rs.getLong("balance_after"),
                new CategoryView(rs.getString("category_code"), rs.getString("category_name")),
                rs.getString("description"),
                new CounterpartyView(rs.getString("counterparty_name"),
                        rs.getObject("counterparty_account_id", UUID.class)));
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

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
