package io.github.guipalm4.ledger.baseline.web;

import io.github.guipalm4.ledger.baseline.command.CommandOutcome;
import io.github.guipalm4.ledger.baseline.command.CommandOutcome.Completed;
import io.github.guipalm4.ledger.baseline.command.CommandOutcome.Rejected;
import io.github.guipalm4.ledger.baseline.command.CommandResult.AccountOpened;
import io.github.guipalm4.ledger.baseline.command.CommandResult.TransactionPosted;
import io.github.guipalm4.ledger.baseline.command.CommandService;
import io.github.guipalm4.ledger.baseline.web.Requests.MovementRequest;
import io.github.guipalm4.ledger.baseline.web.Requests.OpenAccountRequest;
import io.github.guipalm4.ledger.baseline.web.Requests.TransferRequest;
import io.github.guipalm4.ledger.domain.LedgerCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Comandos", description = "Todo comando exige Idempotency-Key; o reenvio devolve o resultado original")
class CommandController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";
    private static final String KEY_DOC = "Chave única por pedido. Reenvio com a mesma chave e o mesmo corpo "
            + "devolve a resposta original (inclusive recusas) com o header Idempotent-Replayed: true";

    private final CommandService commands;

    CommandController(CommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/accounts")
    @Operation(summary = "Abre uma conta (uma por titular e moeda)")
    @ApiResponse(responseCode = "201", description = "Aceito; com Idempotent-Replayed: true quando é reenvio")
    @ApiResponse(responseCode = "400", description = "Entrada malformada ou sem Idempotency-Key",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "ACCOUNT_ALREADY_EXISTS",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Idempotency-Key reutilizada com outro corpo",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<AccountOpened> open(@Parameter(description = KEY_DOC) @RequestHeader(IDEMPOTENCY_KEY) String key,
            @Valid @RequestBody OpenAccountRequest body) {
        var completed = completed(commands.execute(key, body.toCommand()));
        var result = (AccountOpened) completed.result();
        return marked(ResponseEntity.created(URI.create("/accounts/" + result.accountId())), completed).body(result);
    }

    @PostMapping("/accounts/{accountId}/deposits")
    @Operation(summary = "Deposita na conta")
    @ApiResponse(responseCode = "201", description = "Aceito; com Idempotent-Replayed: true quando é reenvio")
    @ApiResponse(responseCode = "400", description = "Entrada malformada ou sem Idempotency-Key",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "UNKNOWN_CATEGORY, CURRENCY_MISMATCH ou Idempotency-Key reutilizada",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<TransactionPosted> deposit(
            @Parameter(description = KEY_DOC) @RequestHeader(IDEMPOTENCY_KEY) String key,
            @PathVariable UUID accountId, @Valid @RequestBody MovementRequest body) {
        return posted(key, body.toDeposit(accountId));
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    @Operation(summary = "Saca da conta, até saldo + limite")
    @ApiResponse(responseCode = "201", description = "Aceito; com Idempotent-Replayed: true quando é reenvio")
    @ApiResponse(responseCode = "400", description = "Entrada malformada ou sem Idempotency-Key",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "INSUFFICIENT_FUNDS, UNKNOWN_CATEGORY, CURRENCY_MISMATCH ou Idempotency-Key reutilizada",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<TransactionPosted> withdraw(
            @Parameter(description = KEY_DOC) @RequestHeader(IDEMPOTENCY_KEY) String key,
            @PathVariable UUID accountId, @Valid @RequestBody MovementRequest body) {
        return posted(key, body.toWithdraw(accountId));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transfere entre contas da mesma moeda (partida dobrada)")
    @ApiResponse(responseCode = "201", description = "Aceito; com Idempotent-Replayed: true quando é reenvio")
    @ApiResponse(responseCode = "400", description = "Entrada malformada ou sem Idempotency-Key",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "INSUFFICIENT_FUNDS, SAME_ACCOUNT, CURRENCY_MISMATCH ou Idempotency-Key reutilizada",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<TransactionPosted> transfer(
            @Parameter(description = KEY_DOC) @RequestHeader(IDEMPOTENCY_KEY) String key,
            @Valid @RequestBody TransferRequest body) {
        return posted(key, body.toCommand());
    }

    private ResponseEntity<TransactionPosted> posted(String key, LedgerCommand command) {
        var completed = completed(commands.execute(key, command));
        return marked(ResponseEntity.status(HttpStatus.CREATED), completed)
                .body((TransactionPosted) completed.result());
    }

    private static Completed completed(CommandOutcome outcome) {
        return switch (outcome) {
            case Completed c -> c;
            case Rejected r -> throw new CommandRejectedException(r);
        };
    }

    private static ResponseEntity.BodyBuilder marked(ResponseEntity.BodyBuilder builder, CommandOutcome outcome) {
        return outcome.replayed() ? builder.header(REPLAYED, "true") : builder;
    }
}
