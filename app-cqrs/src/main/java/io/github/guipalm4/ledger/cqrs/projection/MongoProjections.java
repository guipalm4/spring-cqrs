package io.github.guipalm4.ledger.cqrs.projection;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.push;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import io.github.guipalm4.ledger.cqrs.Roles;
import io.github.guipalm4.ledger.cqrs.event.EntryPosted;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Projeções da v3 no MongoDB, com a forma das tabelas da v2 (ADR-0011, ADR-0012): {@code statement_view}, um
 * documento por conta e mês com os lançamentos no formato do extrato, e {@code monthly_summary}, um documento por
 * conta, mês e categoria com os totais.
 * <p>
 * Cada escrita é idempotente e não depende da ordem de chegada: o documento guarda os ids que já recebeu, e o filtro
 * só casa se o id ainda não estiver lá. Um lançamento repetido cai no upsert e colide no índice único ({@code E11000}),
 * o que quer dizer "já aplicado". Não há disputa pelo mesmo documento: a conta é a chave da partição, e cada partição
 * é consumida por uma thread só.
 * <p>
 * Chaves curtas nos lançamentos, como no {@code statement_entry()} da v2, exceto o instante: {@code ts} é em
 * microssegundos desde a época, porque o {@code Date} do BSON só guarda milissegundos e o Postgres guarda micro.
 */
@Component
@Profile(Roles.PROJECTOR)
public class MongoProjections {

    public static final String STATEMENTS = "statement_view";
    public static final String SUMMARIES = "monthly_summary";

    private static final UpdateOptions UPSERT = new UpdateOptions().upsert(true);

    private final MongoCollection<Document> statements;
    private final MongoCollection<Document> summaries;

    MongoProjections(MongoTemplate mongo) {
        this.statements = mongo.getCollection(STATEMENTS);
        this.summaries = mongo.getCollection(SUMMARIES);
    }

    /**
     * Índices de escrita e de leitura. O text index tem a conta como prefixo (o Q4 sempre filtra por conta) e não
     * usa idioma: sem stemming nem stop words, como o {@code 'simple'} da v1. Criar um índice que já existe não faz nada.
     */
    public void createIndexes() {
        statements.createIndex(Indexes.ascending("account", "month"), new IndexOptions().unique(true));
        statements.createIndex(Indexes.compoundIndex(Indexes.ascending("account"),
                Indexes.compoundIndex(Indexes.text("entries.dsc"), Indexes.text("entries.cp"))),
                new IndexOptions().defaultLanguage("none").name("account_text"));
        summaries.createIndex(Indexes.ascending("account", "month", "cat"), new IndexOptions().unique(true));
    }

    /** Aplica o lançamento nas duas projeções; devolve {@code false} se o extrato já o tinha. */
    public boolean apply(EntryPosted event) {
        var month = month(event.occurredAt());
        boolean fresh = upsertOnce(statements,
                and(eq("account", event.accountId()), eq("month", month), ne("entries.id", event.entryId())),
                push("entries", entry(event)));
        upsertOnce(summaries,
                and(eq("account", event.accountId()), eq("month", month), eq("cat", (int) event.categoryId()),
                        ne("applied", event.entryId())),
                combine(inc("credits", Math.max(event.amount(), 0)), inc("debits", Math.max(-event.amount(), 0)),
                        inc("entries", 1), push("applied", event.entryId())));
        return fresh;
    }

    /** Mês (UTC) do lançamento como {@code yyyy-MM}: a chave dos blocos e do resumo, que ordena como texto. */
    public static String month(Instant occurredAt) {
        return YearMonth.from(occurredAt.atOffset(ZoneOffset.UTC)).toString();
    }

    public static long micros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    private static Document entry(EntryPosted event) {
        return new Document("id", event.entryId())
                .append("tx", event.transactionId())
                .append("ts", micros(event.occurredAt()))
                .append("kind", event.kind().name())
                .append("amt", event.amount())
                .append("bal", event.balanceAfter())
                .append("cat", (int) event.categoryId())
                .append("dsc", event.description())
                .append("cp", event.counterpartyName())
                .append("cpa", event.counterpartyAccountId());
    }

    private static boolean upsertOnce(MongoCollection<Document> collection, Bson filter, Bson update) {
        try {
            collection.updateOne(filter, update, UPSERT);
            return true;
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw e;
        }
    }
}
