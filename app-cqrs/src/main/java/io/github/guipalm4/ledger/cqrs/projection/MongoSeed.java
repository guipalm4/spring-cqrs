package io.github.guipalm4.ledger.cqrs.projection;

import io.github.guipalm4.ledger.cqrs.Roles;
import io.github.guipalm4.ledger.cqrs.event.EntryPosted;
import io.github.guipalm4.ledger.domain.EntryKind;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carga inicial das projeções do Mongo no {@code make seed} da v3 (ADR-0012): lê o ledger recém-carregado no
 * Postgres, uma conta por vez, e grava os documentos com as mesmas peças que o projetor usa
 * ({@link MongoProjections#insert}). Roda no container do projetor, sem servidor web e com o listener do Kafka
 * parado. Com {@code ledger.seed-mongo-exit}, o processo sai ao terminar: sem isso, uma thread não-daemon de
 * biblioteca o manteria vivo.
 * <p>
 * O ledger da v3 não tem índice por conta, então a leitura ordena os lançamentos uma vez, no Postgres. O
 * {@code work_mem} maior vale só para essa transação.
 */
@Component
@Profile(Roles.PROJECTOR)
@ConditionalOnBooleanProperty("ledger.seed-mongo")
class MongoSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoSeed.class);
    private static final int ENTRIES_PER_BATCH = 20_000;

    private final MongoProjections projections;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ConfigurableApplicationContext context;
    private final boolean exit;

    MongoSeed(MongoProjections projections, DataSource dataSource, TransactionTemplate transaction,
            ConfigurableApplicationContext context, @Value("${ledger.seed-mongo-exit:false}") boolean exit) {
        this.projections = projections;
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setFetchSize(10_000);
        this.transaction = transaction;
        this.context = context;
        this.exit = exit;
    }

    @Override
    public void run(@Nullable ApplicationArguments args) {
        var started = Instant.now();
        projections.drop();
        long[] loaded = {0};
        transaction.executeWithoutResult(_ -> {
            jdbc.execute("SET LOCAL work_mem = '256MB'");
            var batch = new ArrayList<EntryPosted>();
            UUID[] current = {null};
            jdbc.query("""
                    SELECT id, transaction_id, account_id, occurred_at, kind, amount, balance_after, category_id,
                           description, counterparty_name, counterparty_account_id
                    FROM ledger_entry ORDER BY account_id""", rs -> {
                var account = rs.getObject("account_id", UUID.class);
                // Só fecha o lote na troca de conta: cada conta vai inteira num lote.
                if (!account.equals(current[0]) && batch.size() >= ENTRIES_PER_BATCH) {
                    projections.insert(batch);
                    loaded[0] += batch.size();
                    batch.clear();
                }
                current[0] = account;
                batch.add(new EntryPosted(rs.getLong("id"), rs.getObject("transaction_id", UUID.class), account,
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        EntryKind.valueOf(rs.getString("kind")), rs.getLong("amount"), rs.getLong("balance_after"),
                        rs.getShort("category_id"), rs.getString("description"), rs.getString("counterparty_name"),
                        rs.getObject("counterparty_account_id", UUID.class)));
            });
            projections.insert(batch);
            loaded[0] += batch.size();
        });
        projections.createIndexes();
        log.info("Projeções do Mongo carregadas: {} lançamentos em {} s", loaded[0],
                Duration.between(started, Instant.now()).toSeconds());
        if (exit) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
