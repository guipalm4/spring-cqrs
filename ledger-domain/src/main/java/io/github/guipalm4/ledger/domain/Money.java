package io.github.guipalm4.ledger.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * Valor em unidades menores da moeda (centavos, no caso do BRL). Sem ponto flutuante.
 */
public record Money(long amount, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public boolean isPositive() {
        return amount > 0;
    }
}
