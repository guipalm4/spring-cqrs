package io.github.guipalm4.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Currency BRL = Currency.getInstance("BRL");

    @Test
    void depositIncreasesBalance() {
        assertThat(account(100, 0).deposit(Money.of(50, "BRL")).balance()).isEqualTo(150);
    }

    @Test
    void withdrawCanUseOverdraftUpToTheLimit() {
        assertThat(account(100, 50).withdraw(Money.of(150, "BRL")).balance()).isEqualTo(-50);
    }

    @Test
    void withdrawBeyondBalancePlusLimitIsRejected() {
        assertThatThrownBy(() -> account(100, 50).withdraw(Money.of(151, "BRL")))
                .isInstanceOfSatisfying(LedgerViolation.class,
                        v -> assertThat(v.violation()).isEqualTo(Violation.INSUFFICIENT_FUNDS));
    }

    @Test
    void amountInAnotherCurrencyIsRejected() {
        assertThatThrownBy(() -> account(100, 0).deposit(Money.of(10, "USD")))
                .isInstanceOfSatisfying(LedgerViolation.class,
                        v -> assertThat(v.violation()).isEqualTo(Violation.CURRENCY_MISMATCH));
    }

    @Test
    void nonPositiveAmountIsMalformedInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> account(100, 0).deposit(Money.of(0, "BRL")));
    }

    @Test
    void transferDebitsAndCreditsTheSameAmount() {
        var source = account(100, 0);
        var target = account(10, 0);

        var result = Account.transfer(source, target, Money.of(60, "BRL"));

        assertThat(result.source().balance()).isEqualTo(40);
        assertThat(result.target().balance()).isEqualTo(70);
        assertThat(result.source().balance() - source.balance() + result.target().balance() - target.balance())
                .isZero();
    }

    @Test
    void transferWithoutFundsChangesNeitherAccount() {
        assertThatThrownBy(() -> Account.transfer(account(10, 0), account(0, 0), Money.of(11, "BRL")))
                .isInstanceOfSatisfying(LedgerViolation.class,
                        v -> assertThat(v.violation()).isEqualTo(Violation.INSUFFICIENT_FUNDS));
    }

    @Test
    void transferToTheSameAccountIsRejected() {
        var account = account(100, 0);

        assertThatThrownBy(() -> Account.transfer(account, account, Money.of(1, "BRL")))
                .isInstanceOfSatisfying(LedgerViolation.class,
                        v -> assertThat(v.violation()).isEqualTo(Violation.SAME_ACCOUNT));
    }

    @Test
    void transferBetweenCurrenciesIsRejected() {
        var usd = new Account(UUID.randomUUID(), "h2", "Holder", Currency.getInstance("USD"), 100, 0);

        assertThatThrownBy(() -> Account.transfer(account(100, 0), usd, Money.of(1, "BRL")))
                .isInstanceOfSatisfying(LedgerViolation.class,
                        v -> assertThat(v.violation()).isEqualTo(Violation.CURRENCY_MISMATCH));
    }

    @Test
    void commandsRejectMalformedInput() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OpenAccount(" ", "Holder", BRL, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OpenAccount("h1", "Holder", BRL, -1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Deposit(UUID.randomUUID(), Money.of(-1, "BRL"), "SALARY", "d", "c"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Transfer(UUID.randomUUID(), UUID.randomUUID(), Money.of(1, "BRL"), "x".repeat(201)));
    }

    private static Account account(long balance, long overdraftLimit) {
        return new Account(UUID.randomUUID(), "h1", "Holder", BRL, balance, overdraftLimit);
    }
}
