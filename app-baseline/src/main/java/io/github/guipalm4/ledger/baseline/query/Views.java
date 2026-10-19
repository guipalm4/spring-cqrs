package io.github.guipalm4.ledger.baseline.query;

import io.github.guipalm4.ledger.domain.EntryKind;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Respostas das consultas Q1–Q4. Fazem parte do contrato REST, idêntico em todas as fases. */
public final class Views {

    private Views() {
    }

    /** Q1. */
    public record AccountView(UUID accountId, String holderId, String holderName, String currency, long balance,
            long overdraftLimit) {
    }

    public record CategoryView(String code, String name) {
    }

    /** {@code accountId} só existe quando a contraparte é uma conta do próprio ledger. */
    public record CounterpartyView(String name, @Nullable UUID accountId) {
    }

    public record EntryView(long entryId, UUID transactionId, Instant occurredAt, EntryKind kind, long amount,
            long balanceAfter, CategoryView category, String description, CounterpartyView counterparty) {
    }

    /** Q2. {@code nextCursor} nulo indica a última página. */
    public record StatementPage(UUID accountId, List<EntryView> items, @Nullable String nextCursor) {
    }

    /** Q3: créditos e débitos em valor absoluto. */
    public record MonthlyCategoryTotal(YearMonth month, CategoryView category, long credits, long debits,
            long entries) {
    }

    public record MonthlySummary(UUID accountId, YearMonth from, YearMonth until, List<MonthlyCategoryTotal> items) {
    }

    /** Q4. */
    public record SearchResult(UUID accountId, List<EntryView> items) {
    }
}
