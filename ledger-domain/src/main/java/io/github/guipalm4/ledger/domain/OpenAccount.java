package io.github.guipalm4.ledger.domain;

import java.util.Currency;

/** Abre uma conta. Invariante: uma conta por titular e moeda. */
public record OpenAccount(String holderId, String holderName, Currency currency, long overdraftLimit)
        implements LedgerCommand {

    public OpenAccount {
        Require.text(holderId, "holderId", 64);
        Require.text(holderName, "holderName", 200);
        if (overdraftLimit < 0) {
            throw new IllegalArgumentException("overdraftLimit must not be negative");
        }
    }
}
