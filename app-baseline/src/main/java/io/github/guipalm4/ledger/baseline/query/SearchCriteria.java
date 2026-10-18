package io.github.guipalm4.ledger.baseline.query;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Filtros do Q4, todos opcionais e combinados com E. {@code text}: todas as palavras em descrição ou
 * contraparte. {@code counterparty}: trecho do nome da contraparte. Valores comparados em módulo.
 */
public record SearchCriteria(@Nullable String text, @Nullable String counterparty, @Nullable String category,
        @Nullable Instant from, @Nullable Instant to, @Nullable Long minAmount, @Nullable Long maxAmount, int limit) {
}
