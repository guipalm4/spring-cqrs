package io.github.guipalm4.ledger.cqrs.query;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.regex;
import static com.mongodb.client.model.Filters.text;
import static com.mongodb.client.model.Projections.exclude;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Sorts.descending;

import com.mongodb.client.MongoCollection;
import io.github.guipalm4.ledger.cqrs.Roles;
import io.github.guipalm4.ledger.cqrs.projection.MongoProjections;
import io.github.guipalm4.ledger.cqrs.query.Views.AccountView;
import io.github.guipalm4.ledger.cqrs.query.Views.CounterpartyView;
import io.github.guipalm4.ledger.cqrs.query.Views.EntryView;
import io.github.guipalm4.ledger.cqrs.query.Views.MonthlyCategoryTotal;
import io.github.guipalm4.ledger.cqrs.query.Views.MonthlySummary;
import io.github.guipalm4.ledger.cqrs.query.Views.SearchResult;
import io.github.guipalm4.ledger.cqrs.query.Views.StatementPage;
import io.github.guipalm4.ledger.domain.EntryKind;
import io.github.guipalm4.ledger.domain.LedgerViolation;
import io.github.guipalm4.ledger.domain.Violation;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Q1–Q4 da v3 ({@link AccountQueries}), na {@code query-api} (ADR-0012). Q1 continua lendo a {@code account} no
 * Postgres, como em todas as fases; Q2–Q4 leem só as projeções do Mongo, que o projetor mantém com atraso.
 * <p>
 * O percurso é o da v2: os blocos da conta, do mês mais recente para o mais antigo, abertos um a um até a página
 * encher. O período, o text index e os nomes de contraparte em minúsculas ({@code cps}) descartam blocos inteiros, e
 * cada lançamento é conferido de novo com a regra da v1, aqui em Java.
 * <p>
 * Os blocos vêm em lotes de {@value #BLOCKS_PER_BATCH}: o primeiro lote padrão do driver traria até 101 blocos de
 * uma vez, e uma página quase sempre cabe em um ou dois.
 */
@Service
@Profile(Roles.QUERY)
class MongoAccountQueries implements AccountQueries {

    private static final int BLOCKS_PER_BATCH = 2;
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Comparator<Document> NEWEST_FIRST = Comparator.<Document>comparingLong(e -> number(e, "ts"))
            .thenComparingLong(e -> number(e, "id"))
            .reversed();

    private final JdbcClient jdbc;
    private final Categories categories;
    private final MongoCollection<Document> statements;
    private final MongoCollection<Document> summaries;

    MongoAccountQueries(JdbcClient jdbc, Categories categories, MongoTemplate mongo) {
        this.jdbc = jdbc;
        this.categories = categories;
        this.statements = mongo.getCollection(MongoProjections.STATEMENTS);
        this.summaries = mongo.getCollection(MongoProjections.SUMMARIES);
    }

    /** Igual à v2: o saldo vem do modelo de escrita, com consistência imediata. */
    @Override
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

    @Override
    public StatementPage statement(UUID accountId, Instant from, Instant to, int limit, @Nullable String cursor) {
        requireLimit(limit);
        requirePeriod(from, to);
        var blocks = new ArrayList<>(List.of(eq("account", accountId), gte("month", MongoProjections.month(from)),
                lte("month", MongoProjections.month(to))));
        long fromMicros = MongoProjections.micros(from);
        long toMicros = MongoProjections.micros(to);
        Predicate<Document> keep = e -> number(e, "ts") >= fromMicros && number(e, "ts") < toMicros;
        if (cursor != null) {
            var position = StatementCursor.decode(cursor);
            long at = MongoProjections.micros(position.occurredAt());
            long id = position.entryId();
            blocks.add(lte("month", MongoProjections.month(position.occurredAt())));
            keep = keep.and(e -> number(e, "ts") < at || number(e, "ts") == at && number(e, "id") < id);
        }

        var rows = page(and(blocks), keep, limit + 1);
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

    @Override
    public MonthlySummary monthlySummary(UUID accountId, YearMonth until) {
        var from = until.minusMonths(11);
        var items = new ArrayList<MonthlyCategoryTotal>();
        for (var s : summaries.find(and(eq("account", accountId), gte("month", from.toString()),
                lte("month", until.toString()))).projection(exclude("applied"))) {
            items.add(new MonthlyCategoryTotal(YearMonth.parse(s.getString("month")),
                    categories.byId((short) number(s, "cat")), number(s, "credits"), number(s, "debits"),
                    number(s, "entries")));
        }
        items.sort(Comparator.comparing(MonthlyCategoryTotal::month).reversed()
                .thenComparing(t -> t.category().code()));
        if (items.isEmpty()) {
            requireAccount(accountId);
        }
        return new MonthlySummary(accountId, from, until, items);
    }

    @Override
    public SearchResult search(UUID accountId, SearchCriteria criteria) {
        requireLimit(criteria.limit());
        var blocks = new ArrayList<>(List.of(eq("account", accountId)));
        Predicate<Document> keep = _ -> true;
        if (criteria.text() != null) {
            // O $text do Mongo é OU entre as palavras e ignora acentos: devolve um superconjunto dos blocos com todas
            // elas, e a conferência por lançamento aplica o E da v1.
            var words = words(criteria.text());
            if (words.isEmpty()) {
                keep = _ -> false;
            } else {
                blocks.add(text(String.join(" ", words)));
                keep = keep.and(e -> words(e.getString("dsc") + " " + e.getString("cp")).containsAll(words));
            }
        }
        if (criteria.counterparty() != null) {
            var part = criteria.counterparty().toLowerCase(Locale.ROOT);
            blocks.add(regex("cps", Pattern.quote(part)));
            keep = keep.and(e -> e.getString("cp").toLowerCase(Locale.ROOT).contains(part));
        }
        if (criteria.category() != null) {
            var category = categories.idOf(criteria.category());
            keep = category.isEmpty() ? _ -> false : keep.and(e -> number(e, "cat") == category.get());
        }
        if (criteria.from() != null) {
            long from = MongoProjections.micros(criteria.from());
            blocks.add(gte("month", MongoProjections.month(criteria.from())));
            keep = keep.and(e -> number(e, "ts") >= from);
        }
        if (criteria.to() != null) {
            long to = MongoProjections.micros(criteria.to());
            blocks.add(lte("month", MongoProjections.month(criteria.to())));
            keep = keep.and(e -> number(e, "ts") < to);
        }
        if (criteria.minAmount() != null) {
            long min = criteria.minAmount();
            keep = keep.and(e -> Math.abs(number(e, "amt")) >= min);
        }
        if (criteria.maxAmount() != null) {
            long max = criteria.maxAmount();
            keep = keep.and(e -> Math.abs(number(e, "amt")) <= max);
        }

        var items = page(and(blocks), keep, criteria.limit());
        if (items.isEmpty()) {
            requireAccount(accountId);
        }
        return new SearchResult(accountId, items);
    }

    /**
     * Os blocos saem do mais recente para o mais antigo e cobrem meses disjuntos: ordenar dentro de cada bloco já dá a
     * ordem global, e basta parar no bloco em que a página encheu. O cursor é fechado ao parar no meio.
     */
    private List<EntryView> page(Bson blocks, Predicate<Document> keep, int limit) {
        var page = new ArrayList<Document>();
        try (var cursor = statements.find(blocks).projection(include("entries")).sort(descending("month"))
                .batchSize(BLOCKS_PER_BATCH).cursor()) {
            while (page.size() < limit && cursor.hasNext()) {
                cursor.next().getList("entries", Document.class).stream()
                        .filter(keep)
                        .sorted(NEWEST_FIRST)
                        .forEach(page::add);
            }
        }
        return page.stream().limit(limit).map(this::entry).toList();
    }

    private EntryView entry(Document e) {
        return new EntryView(number(e, "id"), e.get("tx", UUID.class), instant(number(e, "ts")),
                EntryKind.valueOf(e.getString("kind")), number(e, "amt"), number(e, "bal"),
                categories.byId((short) number(e, "cat")), e.getString("dsc"),
                new CounterpartyView(e.getString("cp"), e.get("cpa", UUID.class)));
    }

    /**
     * Palavras como o parser {@code 'simple'} do Postgres as vê no vocabulário do ledger: sequências de letras e
     * dígitos, em minúsculas, com acento. Diverge do Postgres em termos com pontuação interna (hífen, ponto, e-mail),
     * que o Postgres guarda também inteiros; o dataset e o benchmark não têm nenhum (ADR-0012).
     */
    static Set<String> words(String text) {
        return Arrays.stream(NOT_WORD.split(text.toLowerCase(Locale.ROOT)))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Números do BSON podem vir como int32 ou int64, conforme quem gravou (o projetor ou a carga do seed). */
    private static long number(Document document, String key) {
        return ((Number) document.get(key)).longValue();
    }

    private static Instant instant(long micros) {
        return Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
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
}
