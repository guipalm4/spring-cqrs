package io.github.guipalm4.ledger.baseline.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

interface LedgerEntryRepository extends Repository<LedgerEntryRow, Long> {

    @Modifying
    @Query("""
            INSERT INTO ledger_entry (transaction_id, account_id, kind, amount, balance_after, category_id,
                                      description, counterparty_name, counterparty_account_id, occurred_at)
            VALUES (:transactionId, :accountId, :kind, :amount, :balanceAfter, :categoryId,
                    :description, :counterpartyName, :counterpartyAccountId, now())""")
    void append(UUID transactionId, UUID accountId, String kind, long amount, long balanceAfter, short categoryId,
            String description, String counterpartyName, @Nullable UUID counterpartyAccountId);
}
