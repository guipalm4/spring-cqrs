package io.github.guipalm4.ledger.cqrs.command;

import io.github.guipalm4.ledger.cqrs.command.CommandOutcome.Completed;
import io.github.guipalm4.ledger.cqrs.command.CommandOutcome.Rejected;
import io.github.guipalm4.ledger.cqrs.command.CommandResult.AccountOpened;
import io.github.guipalm4.ledger.cqrs.command.CommandResult.Posting;
import io.github.guipalm4.ledger.cqrs.command.CommandResult.TransactionPosted;
import io.github.guipalm4.ledger.cqrs.projection.Projections;
import io.github.guipalm4.ledger.domain.Account;
import io.github.guipalm4.ledger.domain.Deposit;
import io.github.guipalm4.ledger.domain.EntryKind;
import io.github.guipalm4.ledger.domain.LedgerCommand;
import io.github.guipalm4.ledger.domain.LedgerViolation;
import io.github.guipalm4.ledger.domain.OpenAccount;
import io.github.guipalm4.ledger.domain.Transfer;
import io.github.guipalm4.ledger.domain.Violation;
import io.github.guipalm4.ledger.domain.Withdraw;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Executa comandos com idempotência, numa transação por comando. Cada lançamento atualiza as projeções de
 * leitura na mesma transação (v2, ADR-0011).
 * <p>
 * Toda recusa de negócio acontece antes da primeira escrita do comando (locks e validações primeiro). Por
 * isso a transação pode seguir após uma {@link LedgerViolation} e gravar a recusa junto com a chave.
 */
@Service
public class CommandService {

    private static final String TRANSFER_CATEGORY = "TRANSFER";

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final CategoryRepository categories;
    private final IdempotencyRepository idempotency;
    private final Projections projections;
    private final JsonMapper json;

    CommandService(AccountRepository accounts, LedgerEntryRepository entries, CategoryRepository categories,
            IdempotencyRepository idempotency, Projections projections, JsonMapper json) {
        this.accounts = accounts;
        this.entries = entries;
        this.categories = categories;
        this.idempotency = idempotency;
        this.projections = projections;
        this.json = json;
    }

    @Transactional
    public CommandOutcome execute(String idempotencyKey, LedgerCommand command) {
        byte[] requestHash = sha256(command.toString());
        if (!idempotency.claim(idempotencyKey, requestHash)) {
            return replay(idempotencyKey, requestHash, command);
        }
        try {
            CommandResult result = switch (command) {
                case OpenAccount c -> open(c);
                case Deposit c -> deposit(c);
                case Withdraw c -> withdraw(c);
                case Transfer c -> transfer(c);
            };
            idempotency.complete(idempotencyKey, json.writeValueAsString(result));
            return new Completed(result, false);
        } catch (LedgerViolation v) {
            idempotency.reject(idempotencyKey, v.violation().name(), v.detail());
            return new Rejected(v.violation(), v.detail(), false);
        }
    }

    private CommandOutcome replay(String key, byte[] requestHash, LedgerCommand command) {
        var record = idempotency.findByKey(key).orElseThrow();
        if (!Arrays.equals(record.requestHash(), requestHash)) {
            throw new IdempotencyKeyReusedException(key);
        }
        if (record.violation() != null) {
            return new Rejected(Violation.valueOf(record.violation()), String.valueOf(record.detail()), true);
        }
        if (record.response() == null) {
            throw new IllegalStateException("idempotency record " + key + " has neither response nor violation");
        }
        Class<? extends CommandResult> type = switch (command) {
            case OpenAccount _ -> AccountOpened.class;
            case Deposit _, Withdraw _, Transfer _ -> TransactionPosted.class;
        };
        return new Completed(json.readValue(record.response(), type), true);
    }

    private AccountOpened open(OpenAccount c) {
        var id = accounts.insertIfAbsent(c.holderId(), c.holderName(), c.currency().getCurrencyCode(),
                c.overdraftLimit()).orElseThrow(() -> new LedgerViolation(Violation.ACCOUNT_ALREADY_EXISTS,
                        "holder %s already has a %s account".formatted(c.holderId(), c.currency())));
        return new AccountOpened(id);
    }

    private TransactionPosted deposit(Deposit c) {
        short categoryId = category(c.category());
        var before = lock(c.accountId());
        var after = before.deposit(c.amount());
        var transactionId = UUID.randomUUID();
        return new TransactionPosted(transactionId, List.of(post(transactionId, EntryKind.DEPOSIT, before, after,
                categoryId, c.description(), c.counterparty(), null)));
    }

    private TransactionPosted withdraw(Withdraw c) {
        short categoryId = category(c.category());
        var before = lock(c.accountId());
        var after = before.withdraw(c.amount());
        var transactionId = UUID.randomUUID();
        return new TransactionPosted(transactionId, List.of(post(transactionId, EntryKind.WITHDRAWAL, before, after,
                categoryId, c.description(), c.counterparty(), null)));
    }

    private TransactionPosted transfer(Transfer c) {
        short categoryId = category(TRANSFER_CATEGORY);
        // Ordem de lock determinística: transferências opostas entre as mesmas contas não se travam.
        boolean sourceFirst = c.sourceAccountId().compareTo(c.targetAccountId()) <= 0;
        var first = lock(sourceFirst ? c.sourceAccountId() : c.targetAccountId());
        var second = lock(sourceFirst ? c.targetAccountId() : c.sourceAccountId());
        var source = sourceFirst ? first : second;
        var target = sourceFirst ? second : first;

        var result = Account.transfer(source, target, c.amount());
        var transactionId = UUID.randomUUID();
        return new TransactionPosted(transactionId, List.of(
                post(transactionId, EntryKind.TRANSFER_OUT, source, result.source(), categoryId, c.description(),
                        target.holderName(), target.id()),
                post(transactionId, EntryKind.TRANSFER_IN, target, result.target(), categoryId, c.description(),
                        source.holderName(), source.id())));
    }

    private Posting post(UUID transactionId, EntryKind kind, Account before, Account after, short categoryId,
            String description, String counterpartyName, @Nullable UUID counterpartyAccountId) {
        long amount = after.balance() - before.balance();
        accounts.updateBalance(after.id(), after.balance());
        long entryId = entries.append(transactionId, after.id(), kind.name(), amount, after.balance(), categoryId,
                description, counterpartyName, counterpartyAccountId);
        projections.apply(entryId);
        return new Posting(after.id(), amount, after.balance());
    }

    private Account lock(UUID accountId) {
        return accounts.lockById(accountId).map(AccountRow::toDomain).orElseThrow(
                () -> new LedgerViolation(Violation.ACCOUNT_NOT_FOUND, "account %s not found".formatted(accountId)));
    }

    private short category(String code) {
        return categories.findIdByCode(code).orElseThrow(
                () -> new LedgerViolation(Violation.UNKNOWN_CATEGORY, "unknown category " + code));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
