package io.github.guipalm4.ledger.domain;

import java.util.UUID;

/** Crédito vindo de fora do ledger (salário, estorno...). */
public record Deposit(UUID accountId, Money amount, String category, String description, String counterparty)
        implements LedgerCommand {

    public Deposit {
        Require.positive(amount);
        Require.text(category, "category", 32);
        Require.text(description, "description", 200);
        Require.text(counterparty, "counterparty", 200);
    }
}
