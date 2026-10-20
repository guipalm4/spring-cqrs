-- EXPLAIN ANALYZE de Q1–Q4 da v1, com o mesmo SQL de AccountQueries (app-baseline). Rodado pelo make bench
-- ao fim das execuções (cache aquecido pela carga); a saída vai para docs/benchmarks/<data>-v1/explain.md.
-- Contas fixas do seed: a primeira empresarial e uma de pessoa física.

\pset pager off
SELECT id AS business FROM account WHERE holder_id = 'seed-b-0000001' \gset
SELECT id AS retail FROM account WHERE holder_id = 'seed-r-0050000' \gset

\echo '# EXPLAIN ANALYZE — v1'
\echo
\echo 'Gerado por `make bench` (bench/explain/v1.sql) depois das execuções. Conta empresarial `seed-b-0000001`;'
\echo 'pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.'

\echo
\echo '## Q1 — saldo (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, holder_id, holder_name, currency, balance, overdraft_limit FROM account WHERE id = :'business';
\echo '```'

\echo
\echo '## Q2 — extrato do trimestre, primeira página (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.id, e.transaction_id, e.occurred_at, e.kind, e.amount, e.balance_after, c.code AS category_code,
       c.name AS category_name, e.description, e.counterparty_name, e.counterparty_account_id
FROM ledger_entry e JOIN category c ON c.id = e.category_id
WHERE e.account_id = :'business' AND e.occurred_at >= '2026-01-01Z' AND e.occurred_at < '2026-04-01Z'
ORDER BY e.occurred_at DESC, e.id DESC LIMIT 51;
\echo '```'

\echo
\echo '## Q2 — extrato do trimestre, primeira página (pessoa física)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.id, e.transaction_id, e.occurred_at, e.kind, e.amount, e.balance_after, c.code AS category_code,
       c.name AS category_name, e.description, e.counterparty_name, e.counterparty_account_id
FROM ledger_entry e JOIN category c ON c.id = e.category_id
WHERE e.account_id = :'retail' AND e.occurred_at >= '2026-01-01Z' AND e.occurred_at < '2026-04-01Z'
ORDER BY e.occurred_at DESC, e.id DESC LIMIT 51;
\echo '```'

\echo
\echo '## Q3 — resumo mensal de 12 meses (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT to_char(date_trunc('month', e.occurred_at AT TIME ZONE 'UTC'), 'YYYY-MM') AS month, c.code, c.name,
       coalesce(sum(e.amount) FILTER (WHERE e.amount > 0), 0) AS credits,
       coalesce(-sum(e.amount) FILTER (WHERE e.amount < 0), 0) AS debits, count(*) AS entries
FROM ledger_entry e JOIN category c ON c.id = e.category_id
WHERE e.account_id = :'business' AND e.occurred_at >= '2025-10-01Z' AND e.occurred_at < '2026-10-01Z'
GROUP BY 1, c.code, c.name
ORDER BY 1 DESC, c.code;
\echo '```'

\echo
\echo '## Q4 — termo comum (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.id, e.transaction_id, e.occurred_at, e.kind, e.amount, e.balance_after, c.code AS category_code,
       c.name AS category_name, e.description, e.counterparty_name, e.counterparty_account_id
FROM ledger_entry e JOIN category c ON c.id = e.category_id
WHERE e.account_id = :'business' AND e.search_vector @@ plainto_tsquery('simple', 'supermercado')
ORDER BY e.occurred_at DESC, e.id DESC LIMIT 50;
\echo '```'

\echo
\echo '## Q4 — termo raro (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.id, e.transaction_id, e.occurred_at, e.kind, e.amount, e.balance_after, c.code AS category_code,
       c.name AS category_name, e.description, e.counterparty_name, e.counterparty_account_id
FROM ledger_entry e JOIN category c ON c.id = e.category_id
WHERE e.account_id = :'business' AND e.search_vector @@ plainto_tsquery('simple', 'sushi kaze')
ORDER BY e.occurred_at DESC, e.id DESC LIMIT 50;
\echo '```'

\echo
\echo '## Q4 — contraparte (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.id, e.transaction_id, e.occurred_at, e.kind, e.amount, e.balance_after, c.code AS category_code,
       c.name AS category_name, e.description, e.counterparty_name, e.counterparty_account_id
FROM ledger_entry e JOIN category c ON c.id = e.category_id
WHERE e.account_id = :'business' AND e.counterparty_name ILIKE '%nonna%'
ORDER BY e.occurred_at DESC, e.id DESC LIMIT 50;
\echo '```'

\echo
\echo '## Q4 — texto + categoria + período (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT e.id, e.transaction_id, e.occurred_at, e.kind, e.amount, e.balance_after, c.code AS category_code,
       c.name AS category_name, e.description, e.counterparty_name, e.counterparty_account_id
FROM ledger_entry e JOIN category c ON c.id = e.category_id
WHERE e.account_id = :'business' AND e.search_vector @@ plainto_tsquery('simple', 'farmácia')
  AND c.code = 'HEALTH' AND e.occurred_at >= '2026-01-01Z'
ORDER BY e.occurred_at DESC, e.id DESC LIMIT 50;
\echo '```'
