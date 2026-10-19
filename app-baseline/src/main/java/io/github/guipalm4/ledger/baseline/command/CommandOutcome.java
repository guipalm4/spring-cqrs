package io.github.guipalm4.ledger.baseline.command;

import io.github.guipalm4.ledger.domain.Violation;

/** Resultado de executar um comando; {@code replayed} indica que veio do registro de idempotência. */
public sealed interface CommandOutcome {

    boolean replayed();

    record Completed(CommandResult result, boolean replayed) implements CommandOutcome {
    }

    record Rejected(Violation violation, String detail, boolean replayed) implements CommandOutcome {
    }
}
