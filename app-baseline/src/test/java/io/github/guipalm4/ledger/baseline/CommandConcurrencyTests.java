package io.github.guipalm4.ledger.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guipalm4.ledger.baseline.command.CommandOutcome;
import io.github.guipalm4.ledger.baseline.command.CommandOutcome.Completed;
import io.github.guipalm4.ledger.baseline.command.CommandOutcome.Rejected;
import io.github.guipalm4.ledger.baseline.command.CommandResult.AccountOpened;
import io.github.guipalm4.ledger.baseline.command.CommandResult.TransactionPosted;
import io.github.guipalm4.ledger.baseline.command.CommandService;
import io.github.guipalm4.ledger.baseline.command.IdempotencyKeyReusedException;
import io.github.guipalm4.ledger.domain.Deposit;
import io.github.guipalm4.ledger.domain.LedgerCommand;
import io.github.guipalm4.ledger.domain.Money;
import io.github.guipalm4.ledger.domain.OpenAccount;
import io.github.guipalm4.ledger.domain.Transfer;
import io.github.guipalm4.ledger.domain.Violation;
import io.github.guipalm4.ledger.domain.Withdraw;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Invariantes sob concorrência real: transações paralelas no Postgres, sem mocks. */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class CommandConcurrencyTests {

    @Autowired
    CommandService commands;

    @Autowired
    JdbcClient jdbc;

    @Test
    void opposingTransfersNeitherDeadlockNorLoseMoney() throws Exception {
        var a = openWith(100_000, 0);
        var b = openWith(100_000, 0);

        var outcomes = concurrently(200, i -> i % 2 == 0
                ? new Transfer(a, b, brl(100 + i), "a→b " + i)
                : new Transfer(b, a, brl(100 + i), "b→a " + i));

        assertThat(outcomes).allSatisfy(o -> assertThat(o).isInstanceOf(Completed.class));
        long aOut = sum(0, 200, 2);
        long bOut = sum(1, 200, 2);
        assertThat(balance(a)).isEqualTo(100_000 - aOut + bOut);
        assertThat(balance(b)).isEqualTo(100_000 - bOut + aOut);
        LedgerInvariants.assertHold(jdbc);
    }

    @Test
    void concurrentWithdrawalsNeverExceedBalancePlusLimit() throws Exception {
        var account = openWith(1_000, 500);

        var outcomes = concurrently(50, i -> new Withdraw(account, brl(100), "GROCERIES", "compra " + i, "Mercado"));

        assertThat(outcomes).filteredOn(Completed.class::isInstance).hasSize(15);
        assertThat(outcomes).filteredOn(Rejected.class::isInstance)
                .allSatisfy(o -> assertThat(((Rejected) o).violation()).isEqualTo(Violation.INSUFFICIENT_FUNDS));
        assertThat(balance(account)).isEqualTo(-500);
        LedgerInvariants.assertHold(jdbc);
    }

    @Test
    void sameKeySentConcurrentlyTakesEffectOnce() throws Exception {
        var account = openWith(0, 0);
        var key = UUID.randomUUID().toString();
        var deposit = new Deposit(account, brl(700), "SALARY", "salário", "Empresa X");

        var outcomes = concurrently(20, _ -> deposit, _ -> key);

        var transactionIds = outcomes.stream()
                .map(o -> ((TransactionPosted) ((Completed) o).result()).transactionId()).distinct().toList();
        assertThat(transactionIds).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.replayed()).hasSize(1);
        assertThat(balance(account)).isEqualTo(700);
    }

    @Test
    void rejectionIsReplayedEvenAfterFundsArrive() {
        var account = openWith(0, 0);
        var key = UUID.randomUUID().toString();
        var withdraw = new Withdraw(account, brl(100), "GROCERIES", "compra", "Mercado");

        assertThat(commands.execute(key, withdraw)).isInstanceOf(Rejected.class);
        commands.execute(UUID.randomUUID().toString(), new Deposit(account, brl(500), "SALARY", "s", "Empresa X"));

        assertThat(commands.execute(key, withdraw))
                .isEqualTo(new Rejected(Violation.INSUFFICIENT_FUNDS,
                        ((Rejected) commands.execute(key, withdraw)).detail(), true));
        assertThat(balance(account)).isEqualTo(500);
    }

    @Test
    void keyReusedWithAnotherPayloadIsRefused() {
        var account = openWith(0, 0);
        var key = UUID.randomUUID().toString();
        commands.execute(key, new Deposit(account, brl(100), "SALARY", "s", "Empresa X"));

        assertThatThrownBy(() -> commands.execute(key, new Deposit(account, brl(999), "SALARY", "s", "Empresa X")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(balance(account)).isEqualTo(100);
    }

    @Test
    void secondAccountForSameHolderAndCurrencyIsRejected() {
        var holder = UUID.randomUUID().toString();
        var open = new OpenAccount(holder, "Titular", Currency.getInstance("BRL"), 0);
        commands.execute(UUID.randomUUID().toString(), open);

        assertThat(commands.execute(UUID.randomUUID().toString(), open))
                .isInstanceOfSatisfying(Rejected.class,
                        r -> assertThat(r.violation()).isEqualTo(Violation.ACCOUNT_ALREADY_EXISTS));
        assertThat(commands.execute(UUID.randomUUID().toString(),
                new OpenAccount(holder, "Titular", Currency.getInstance("USD"), 0)))
                .isInstanceOf(Completed.class);
    }

    private List<CommandOutcome> concurrently(int n, IntFunction<LedgerCommand> command) throws Exception {
        return concurrently(n, command, _ -> UUID.randomUUID().toString());
    }

    private List<CommandOutcome> concurrently(int n, IntFunction<LedgerCommand> command, IntFunction<String> key)
            throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<CommandOutcome>>();
            for (int i = 0; i < n; i++) {
                int index = i;
                Callable<CommandOutcome> task = () -> {
                    start.await();
                    return commands.execute(key.apply(index), command.apply(index));
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            var outcomes = new ArrayList<CommandOutcome>();
            for (var future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }

    private UUID openWith(long balance, long overdraftLimit) {
        var opened = (Completed) commands.execute(UUID.randomUUID().toString(),
                new OpenAccount(UUID.randomUUID().toString(), "Titular", Currency.getInstance("BRL"), overdraftLimit));
        var id = ((AccountOpened) opened.result()).accountId();
        if (balance > 0) {
            commands.execute(UUID.randomUUID().toString(), new Deposit(id, brl(balance), "SALARY", "inicial", "Empresa X"));
        }
        return id;
    }

    private long balance(UUID account) {
        return jdbc.sql("SELECT balance FROM account WHERE id = :id").param("id", account).query(Long.class).single();
    }

    private static long sum(int first, int end, int step) {
        long total = 0;
        for (int i = first; i < end; i += step) {
            total += 100 + i;
        }
        return total;
    }

    private static Money brl(long amount) {
        return Money.of(amount, "BRL");
    }
}
