package io.github.guipalm4.ledger.cqrs.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

interface LedgerEntryRepository extends Repository<LedgerEntryRow, Long> {

    /** O id e o {@code occurred_at} vêm do banco e seguem no evento {@code EntryPosted}. */
    @Query("""
            INSERT INTO ledger_entry (transaction_id, account_id, kind, amount, balance_after, category_id,
                                      description, counterparty_name, counterparty_account_id, occurred_at)
            VALUES (:transactionId, :accountId, :kind, :amount, :balanceAfter, :categoryId,
                    :description, :counterpartyName, :counterpartyAccountId, now())
            RETURNING id, transaction_id, account_id, amount, balance_after, occurred_at""")
    LedgerEntryRow append(UUID transactionId, UUID accountId, String kind, long amount, long balanceAfter, short categoryId,
            String description, String counterpartyName, @Nullable UUID counterpartyAccountId);
}
