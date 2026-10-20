package io.github.guipalm4.ledger.cqrs.command;

import java.util.List;
import java.util.UUID;

/** Resposta de um comando aceito. Faz parte do contrato REST, idêntico em todas as fases. */
public sealed interface CommandResult {

    record AccountOpened(UUID accountId) implements CommandResult {
    }

    record TransactionPosted(UUID transactionId, List<Posting> postings) implements CommandResult {
    }

    /** Efeito de um lançamento numa conta: valor com sinal e saldo resultante. */
    record Posting(UUID accountId, long amount, long balanceAfter) {
    }
}
