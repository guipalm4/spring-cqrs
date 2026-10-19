package io.github.guipalm4.ledger.baseline.command;

/** A chave já foi usada com outro conteúdo; reaproveitá-la seria devolver o resultado de outro pedido. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key '%s' was already used with a different request".formatted(key));
    }
}
