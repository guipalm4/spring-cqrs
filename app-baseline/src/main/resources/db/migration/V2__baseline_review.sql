-- Revisão da baseline no M2, a partir do EXPLAIN ANALYZE sobre o dataset de referência (ADR-0009).
-- Idempotente: as mesmas mudanças foram aplicadas à mão no banco usado na investigação.

-- 1% das contas concentra ~44% dos lançamentos (ADR-0008). Com o alvo padrão (100), a lista de valores mais
-- comuns não alcança as contas empresariais e o planner estima ~640 linhas onde há ~7.700.
ALTER TABLE ledger_entry ALTER COLUMN account_id SET STATISTICS 2000;

-- O Q4 é sempre por conta. Com GIN só sobre texto, o Postgres cruza o bitmap da conta com o do termo
-- (centenas de milhares de linhas) ou filtra todos os lançamentos da conta; com account_id dentro do GIN,
-- o índice já devolve só as linhas da conta que casam.
CREATE EXTENSION IF NOT EXISTS btree_gin;

DROP INDEX IF EXISTS ledger_entry_search_idx;
DROP INDEX IF EXISTS ledger_entry_counterparty_trgm_idx;

CREATE INDEX IF NOT EXISTS ledger_entry_account_search_idx
    ON ledger_entry USING gin (account_id, search_vector);
CREATE INDEX IF NOT EXISTS ledger_entry_account_counterparty_trgm_idx
    ON ledger_entry USING gin (account_id, counterparty_name gin_trgm_ops);
