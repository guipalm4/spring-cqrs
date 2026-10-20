package io.github.guipalm4.ledger.cqrs.web;

import io.github.guipalm4.ledger.cqrs.command.CommandOutcome.Rejected;

/** Leva uma recusa até o {@link ProblemHandler}, que a transforma em resposta HTTP. */
class CommandRejectedException extends RuntimeException {

    private final Rejected rejected;

    CommandRejectedException(Rejected rejected) {
        super(rejected.detail(), null, false, false);
        this.rejected = rejected;
    }

    Rejected rejected() {
        return rejected;
    }
}
