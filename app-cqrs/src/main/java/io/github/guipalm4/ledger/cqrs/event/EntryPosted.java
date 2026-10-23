package io.github.guipalm4.ledger.cqrs.event;

import io.github.guipalm4.ledger.domain.EntryKind;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.events.Externalized;

/**
 * Um lançamento gravado no ledger, publicado na transação do comando (ADR-0004, ADR-0012).
 * <p>
 * Na v2, um listener síncrono atualiza as projeções do Postgres na mesma transação. Na v3, o outbox do Modulith
 * o envia ao Kafka depois do commit, com a conta como chave (ordem por conta dentro da partição), e o projetor o
 * aplica no Mongo. Carrega tudo o que a projeção precisa, para o projetor não ler o Postgres.
 *
 * @param occurredAt o {@code now()} do Postgres, isto é, o início da transação do comando
 */
@Externalized(EntryPosted.TOPIC + "::#{#this.accountId().toString()}")
public record EntryPosted(long entryId, UUID transactionId, UUID accountId, Instant occurredAt, EntryKind kind,
        long amount, long balanceAfter, short categoryId, String description, String counterpartyName,
        @Nullable UUID counterpartyAccountId) {

    public static final String TOPIC = "ledger.entry-posted";
}
