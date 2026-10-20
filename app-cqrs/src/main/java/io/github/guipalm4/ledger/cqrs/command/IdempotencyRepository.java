package io.github.guipalm4.ledger.cqrs.command;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

interface IdempotencyRepository extends Repository<IdempotencyRecord, String> {

    /**
     * Reserva a chave. Se outra transação reservou a mesma chave e ainda não terminou, o Postgres espera
     * por ela; ao fim, devolve {@code false} e o registro dela já está visível.
     */
    @Modifying
    @Query("""
            INSERT INTO idempotency_record (idempotency_key, request_hash) VALUES (:key, :requestHash)
            ON CONFLICT (idempotency_key) DO NOTHING""")
    boolean claim(String key, byte[] requestHash);

    @Query("""
            SELECT idempotency_key, request_hash, response::text AS response, violation, detail
            FROM idempotency_record WHERE idempotency_key = :key""")
    Optional<IdempotencyRecord> findByKey(String key);

    @Modifying
    @Query("UPDATE idempotency_record SET response = CAST(:response AS jsonb) WHERE idempotency_key = :key")
    void complete(String key, String response);

    @Modifying
    @Query("UPDATE idempotency_record SET violation = :violation, detail = :detail WHERE idempotency_key = :key")
    void reject(String key, String violation, String detail);
}
