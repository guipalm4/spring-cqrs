package io.github.guipalm4.ledger.domain;

import java.util.UUID;

/** Transferência entre contas do ledger, na mesma moeda. Invariante: partida dobrada, atômica. */
public record Transfer(UUID sourceAccountId, UUID targetAccountId, Money amount, String description)
        implements LedgerCommand {

    public Transfer {
        Require.positive(amount);
        Require.text(description, "description", 200);
    }
}
