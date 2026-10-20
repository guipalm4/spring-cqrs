package io.github.guipalm4.ledger.cqrs.command;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

interface AccountRepository extends Repository<AccountRow, UUID> {

    /** Vazio quando o titular já tem conta nessa moeda (sem abortar a transação, ao contrário de um erro de unique). */
    @Query("""
            INSERT INTO account (holder_id, holder_name, currency, balance, overdraft_limit)
            VALUES (:holderId, :holderName, :currency, 0, :overdraftLimit)
            ON CONFLICT (holder_id, currency) DO NOTHING
            RETURNING id""")
    Optional<UUID> insertIfAbsent(String holderId, String holderName, String currency, long overdraftLimit);

    @Query("""
            SELECT id, holder_id, holder_name, currency, balance, overdraft_limit
            FROM account WHERE id = :id FOR UPDATE""")
    Optional<AccountRow> lockById(UUID id);

    @Modifying
    @Query("UPDATE account SET balance = :balance WHERE id = :id")
    void updateBalance(UUID id, long balance);
}
