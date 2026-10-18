package io.github.guipalm4.ledger.baseline.query;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/** Posição do último item de uma página do extrato (keyset), opaca para o cliente. */
record StatementCursor(Instant occurredAt, long entryId) {

    String encode() {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, occurredAt);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((micros + ":" + entryId).getBytes(StandardCharsets.US_ASCII));
    }

    static StatementCursor decode(String cursor) {
        try {
            var parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII).split(":", 2);
            return new StatementCursor(Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS),
                    Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid cursor", e);
        }
    }
}
