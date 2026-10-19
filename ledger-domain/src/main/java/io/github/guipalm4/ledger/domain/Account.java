package io.github.guipalm4.ledger.domain;

import java.util.Currency;
import java.util.UUID;

/**
 * Estado de uma conta e as regras que mudam o saldo. Imutável: cada operação devolve o novo estado.
 */
public record Account(UUID id, String holderId, String holderName, Currency currency, long balance,
        long overdraftLimit) {

    public Account {
        if (overdraftLimit < 0) {
            throw new IllegalArgumentException("overdraftLimit must not be negative");
        }
        if (balance < -overdraftLimit) {
            throw new IllegalStateException("balance %d exceeds overdraft limit %d".formatted(balance, overdraftLimit));
        }
    }

    public Account deposit(Money amount) {
        return withBalance(Math.addExact(balance, accepted(amount)));
    }

    /** Saque não ultrapassa saldo + limite. */
    public Account withdraw(Money amount) {
        long newBalance = Math.subtractExact(balance, accepted(amount));
        if (newBalance < -overdraftLimit) {
            throw new LedgerViolation(Violation.INSUFFICIENT_FUNDS,
                    "account %s has %d available, requested %d".formatted(id, balance + overdraftLimit, amount.amount()));
        }
        return withBalance(newBalance);
    }

    /**
     * Partida dobrada: o débito na origem e o crédito no destino têm o mesmo valor, então somam zero.
     * As duas contas mudam juntas ou nenhuma muda.
     */
    public static Transferred transfer(Account source, Account target, Money amount) {
        if (source.id.equals(target.id)) {
            throw new LedgerViolation(Violation.SAME_ACCOUNT, "source and target are the same account");
        }
        if (!source.currency.equals(target.currency)) {
            throw new LedgerViolation(Violation.CURRENCY_MISMATCH,
                    "source is in %s, target is in %s".formatted(source.currency, target.currency));
        }
        return new Transferred(source.withdraw(amount), target.deposit(amount));
    }

    public record Transferred(Account source, Account target) {
    }

    private long accepted(Money amount) {
        if (!amount.currency().equals(currency)) {
            throw new LedgerViolation(Violation.CURRENCY_MISMATCH,
                    "account %s is in %s, amount is in %s".formatted(id, currency, amount.currency()));
        }
        Require.positive(amount);
        return amount.amount();
    }

    private Account withBalance(long newBalance) {
        return new Account(id, holderId, holderName, currency, newBalance, overdraftLimit);
    }
}
