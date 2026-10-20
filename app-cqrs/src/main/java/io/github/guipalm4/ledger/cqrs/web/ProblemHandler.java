package io.github.guipalm4.ledger.cqrs.web;

import io.github.guipalm4.ledger.cqrs.command.IdempotencyKeyReusedException;
import io.github.guipalm4.ledger.domain.LedgerViolation;
import io.github.guipalm4.ledger.domain.Violation;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Erros como RFC 9457 (problem+json). Recusas de negócio levam o código em {@code violation}. */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ResponseEntity<ProblemDetail> rejected(CommandRejectedException e) {
        var rejected = e.rejected();
        var response = ResponseEntity.status(status(rejected.violation()));
        if (rejected.replayed()) {
            response.header(CommandController.REPLAYED, "true");
        }
        return response.body(problem(rejected.violation(), rejected.detail()));
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> violation(LedgerViolation e) {
        return ResponseEntity.status(status(e.violation())).body(problem(e.violation(), e.detail()));
    }

    @ExceptionHandler
    ProblemDetail keyReused(IdempotencyKeyReusedException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setType(URI.create("urn:ledger:idempotency-key-reused"));
        return problem;
    }

    @ExceptionHandler
    ProblemDetail malformed(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private static HttpStatus status(Violation violation) {
        return switch (violation) {
            case ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCOUNT_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case INSUFFICIENT_FUNDS, CURRENCY_MISMATCH, SAME_ACCOUNT, UNKNOWN_CATEGORY ->
                    HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    private static ProblemDetail problem(Violation violation, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status(violation), detail);
        problem.setType(URI.create("urn:ledger:" + violation.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(violation.name());
        problem.setProperty("violation", violation.name());
        return problem;
    }
}
