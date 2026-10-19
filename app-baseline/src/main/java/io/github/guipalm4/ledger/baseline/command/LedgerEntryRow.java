package io.github.guipalm4.ledger.baseline.command;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Só existe para tipar o repositório: lançamentos são gravados por SQL explícito e lidos pelo lado de consulta. */
@Table("ledger_entry")
record LedgerEntryRow(@Id Long id, UUID transactionId, UUID accountId, long amount, long balanceAfter) {
}
