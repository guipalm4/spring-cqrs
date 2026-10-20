package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Invariantes do ledger e das projeções da v2, conferidas direto no banco, para qualquer volume de dados. */
final class LedgerInvariants {

    private LedgerInvariants() {
    }

    static void assertHold(JdbcClient jdbc) {
        assertThat(count(jdbc, """
                SELECT count(*) FROM account a
                LEFT JOIN (SELECT account_id, sum(amount) AS total FROM ledger_entry GROUP BY account_id) e
                       ON e.account_id = a.id
                WHERE a.balance <> coalesce(e.total, 0)"""))
                .as("saldo da conta = soma dos seus lançamentos").isZero();
        assertThat(count(jdbc, """
                SELECT count(*) FROM (
                    SELECT balance_after, sum(amount) OVER (PARTITION BY account_id ORDER BY id) AS running
                    FROM ledger_entry) x
                WHERE balance_after <> running"""))
                .as("balance_after = soma acumulada na ordem dos lançamentos").isZero();
        assertThat(count(jdbc, """
                SELECT count(*) FROM (
                    SELECT transaction_id FROM ledger_entry WHERE kind IN ('TRANSFER_IN', 'TRANSFER_OUT')
                    GROUP BY transaction_id
                    HAVING sum(amount) <> 0 OR count(*) <> 2
                       OR count(*) FILTER (WHERE kind = 'TRANSFER_OUT') <> 1) x"""))
                .as("transferência = par OUT/IN que soma zero").isZero();
        assertThat(count(jdbc, """
                SELECT count(*) FROM ledger_entry e JOIN account a ON a.id = e.account_id
                WHERE e.balance_after < -a.overdraft_limit"""))
                .as("nenhum saldo intermediário passou do limite").isZero();
        assertThat(count(jdbc, """
                SELECT count(*) FROM (
                    SELECT account_id, ledger_month(occurred_at) AS month, count(*) AS n
                    FROM ledger_entry GROUP BY 1, 2) l
                FULL JOIN (SELECT account_id, month, jsonb_array_length(entries) AS n FROM statement_view) v
                       USING (account_id, month)
                WHERE l.n IS DISTINCT FROM v.n"""))
                .as("statement_view tem um bloco por conta e mês com todos os lançamentos").isZero();
        assertThat(count(jdbc, """
                SELECT count(*) FROM (
                    SELECT account_id, ledger_month(occurred_at) AS month, category_id,
                           sum(greatest(amount, 0)) AS credits, sum(greatest(-amount, 0)) AS debits, count(*) AS n
                    FROM ledger_entry GROUP BY 1, 2, 3) l
                FULL JOIN monthly_summary s USING (account_id, month, category_id)
                WHERE (l.credits, l.debits, l.n) IS DISTINCT FROM (s.credits, s.debits, s.entries)"""))
                .as("monthly_summary = totais do ledger por conta, mês e categoria").isZero();
    }

    private static long count(JdbcClient jdbc, String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
