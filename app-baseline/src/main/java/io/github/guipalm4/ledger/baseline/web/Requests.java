package io.github.guipalm4.ledger.baseline.web;

import io.github.guipalm4.ledger.domain.Deposit;
import io.github.guipalm4.ledger.domain.Money;
import io.github.guipalm4.ledger.domain.OpenAccount;
import io.github.guipalm4.ledger.domain.Transfer;
import io.github.guipalm4.ledger.domain.Withdraw;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Currency;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Corpos das requisições de comando. Aqui só se exige presença; forma e regras ficam nos comandos do domínio.
 * Valores sempre em unidades menores da moeda (centavos).
 */
final class Requests {

    private Requests() {
    }

    record OpenAccountRequest(@NotBlank String holderId, @NotBlank String holderName,
            @NotBlank @Schema(example = "BRL") String currency, @Nullable Long overdraftLimit) {

        OpenAccount toCommand() {
            return new OpenAccount(holderId, holderName, Currency.getInstance(currency),
                    overdraftLimit == null ? 0 : overdraftLimit);
        }
    }

    record MovementRequest(@NotNull Long amount, @NotBlank @Schema(example = "BRL") String currency,
            @NotBlank @Schema(example = "GROCERIES") String category, @NotBlank String description,
            @NotBlank String counterparty) {

        Deposit toDeposit(UUID accountId) {
            return new Deposit(accountId, Money.of(amount, currency), category, description, counterparty);
        }

        Withdraw toWithdraw(UUID accountId) {
            return new Withdraw(accountId, Money.of(amount, currency), category, description, counterparty);
        }
    }

    record TransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID targetAccountId, @NotNull Long amount,
            @NotBlank @Schema(example = "BRL") String currency, @NotBlank String description) {

        Transfer toCommand() {
            return new Transfer(sourceAccountId, targetAccountId, Money.of(amount, currency), description);
        }
    }
}
