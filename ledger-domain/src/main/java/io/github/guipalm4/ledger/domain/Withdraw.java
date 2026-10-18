package io.github.guipalm4.ledger.domain;

import java.util.UUID;

/** Débito para fora do ledger (compra, conta paga...). Invariante: não ultrapassa saldo + limite. */
public record Withdraw(UUID accountId, Money amount, String category, String description, String counterparty)
        implements LedgerCommand {

    public Withdraw {
        Require.positive(amount);
        Require.text(category, "category", 32);
        Require.text(description, "description", 200);
        Require.text(counterparty, "counterparty", 200);
    }
}
