package io.github.guipalm4.ledger.domain;

/** Validações de forma compartilhadas pelos comandos. Os limites batem com as colunas do banco. */
final class Require {

    private Require() {
    }

    static void text(String value, String name, int maxLength) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must have at most " + maxLength + " characters");
        }
    }

    static void positive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
