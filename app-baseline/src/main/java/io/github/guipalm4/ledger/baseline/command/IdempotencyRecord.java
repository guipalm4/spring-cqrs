package io.github.guipalm4.ledger.baseline.command;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("idempotency_record")
record IdempotencyRecord(@Id String idempotencyKey, byte[] requestHash, @Nullable String response,
        @Nullable String violation, @Nullable String detail) {
}
