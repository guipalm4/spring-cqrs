package io.github.guipalm4.ledger.baseline.web;

import io.github.guipalm4.ledger.baseline.query.AccountQueries;
import io.github.guipalm4.ledger.baseline.query.SearchCriteria;
import io.github.guipalm4.ledger.baseline.query.Views.AccountView;
import io.github.guipalm4.ledger.baseline.query.Views.MonthlySummary;
import io.github.guipalm4.ledger.baseline.query.Views.SearchResult;
import io.github.guipalm4.ledger.baseline.query.Views.StatementPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Consultas", description = "Q1–Q4 (docs/SCOPE.md). Datas em ISO-8601 UTC; valores em centavos")
class QueryController {

    private final AccountQueries queries;

    QueryController(AccountQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Q1 — saldo atual da conta")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    AccountView account(@PathVariable UUID accountId) {
        return queries.account(accountId);
    }

    @GetMapping("/accounts/{accountId}/statement")
    @Operation(summary = "Q2 — extrato do período [from, to), mais recentes primeiro, paginado por cursor")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    StatementPage statement(@PathVariable UUID accountId, @RequestParam Instant from, @RequestParam Instant to,
            @RequestParam(defaultValue = "50") int limit,
            @Parameter(description = "nextCursor da página anterior") @RequestParam @Nullable String cursor) {
        return queries.statement(accountId, from, to, limit, cursor);
    }

    @GetMapping("/accounts/{accountId}/monthly-summary")
    @Operation(summary = "Q3 — créditos e débitos por mês e categoria nos 12 meses que terminam em until")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    MonthlySummary monthlySummary(@PathVariable UUID accountId,
            @Parameter(description = "Último mês (yyyy-MM); padrão: mês corrente em UTC", example = "2026-09")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") @Nullable YearMonth until) {
        return queries.monthlySummary(accountId, until != null ? until : YearMonth.now(ZoneOffset.UTC));
    }

    @GetMapping("/accounts/{accountId}/entries/search")
    @Operation(summary = "Q4 — busca livre com filtros combinados (E), mais recentes primeiro")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    SearchResult search(@PathVariable UUID accountId,
            @Parameter(description = "Palavras que devem aparecer todas na descrição ou na contraparte")
            @RequestParam @Nullable String q,
            @Parameter(description = "Trecho do nome da contraparte") @RequestParam @Nullable String counterparty,
            @Parameter(description = "Código da categoria", example = "GROCERIES") @RequestParam @Nullable String category,
            @RequestParam @Nullable Instant from, @RequestParam @Nullable Instant to,
            @Parameter(description = "Valor mínimo, em módulo") @RequestParam @Nullable Long minAmount,
            @Parameter(description = "Valor máximo, em módulo") @RequestParam @Nullable Long maxAmount,
            @RequestParam(defaultValue = "50") int limit) {
        return queries.search(accountId, new SearchCriteria(q, counterparty, category, from, to, minAmount, maxAmount,
                limit));
    }
}
