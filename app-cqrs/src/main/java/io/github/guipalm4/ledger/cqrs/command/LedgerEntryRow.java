package io.github.guipalm4.ledger.cqrs.command;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** O lançamento recém-gravado, como volta do {@code RETURNING}: lançamentos são gravados por SQL explícito. */
@Table("ledger_entry")
record LedgerEntryRow(@Id Long id, UUID transactionId, UUID accountId, long amount, long balanceAfter,
        Instant occurredAt) {
}
