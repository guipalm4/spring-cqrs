package io.github.guipalm4.ledger.domain;

/**
 * Recusa de um comando por regra de negócio. Diferente de entrada malformada
 * ({@link IllegalArgumentException}), a recusa faz parte do resultado e é guardada pela idempotência.
 */
public class LedgerViolation extends RuntimeException {

    private final Violation violation;

    public LedgerViolation(Violation violation, String detail) {
        super(detail);
        this.violation = violation;
    }

    public Violation violation() {
        return violation;
    }

    public String detail() {
        return getMessage();
    }
}
