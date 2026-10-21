package io.github.guipalm4.ledger.cqrs.query;

import io.github.guipalm4.ledger.cqrs.query.Views.AccountView;
import io.github.guipalm4.ledger.cqrs.query.Views.MonthlySummary;
import io.github.guipalm4.ledger.cqrs.query.Views.SearchResult;
import io.github.guipalm4.ledger.cqrs.query.Views.StatementPage;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Q1–Q4 (docs/SCOPE.md), com o mesmo contrato em todas as fases: na v2, sobre as projeções do Postgres
 * ({@link PostgresAccountQueries}); na v3, sobre as do Mongo ({@link MongoAccountQueries}).
 */
public interface AccountQueries {

    int MAX_LIMIT = 200;

    /** Q1 — saldo atual. */
    AccountView account(UUID accountId);

    /** Q2 — extrato do período [from, to), do mais recente para o mais antigo, paginado por keyset. */
    StatementPage statement(UUID accountId, Instant from, Instant to, int limit, @Nullable String cursor);

    /** Q3 — totais por mês e categoria nos 12 meses que terminam em {@code until} (UTC). */
    MonthlySummary monthlySummary(UUID accountId, YearMonth until);

    /** Q4 — busca com filtros combinados, do mais recente para o mais antigo. */
    SearchResult search(UUID accountId, SearchCriteria criteria);
}
