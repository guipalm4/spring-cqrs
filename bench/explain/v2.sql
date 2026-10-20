-- EXPLAIN ANALYZE de Q1–Q4 da v2, com o mesmo SQL de AccountQueries (app-cqrs) e os mesmos parâmetros de v1.sql.
-- Rodado pelo make bench ao fim das execuções (cache aquecido pela carga); a saída vai para
-- docs/benchmarks/<data>-v2/explain.md. Contas fixas do seed: a primeira empresarial e uma de pessoa física.

\pset pager off
SELECT id AS business FROM account WHERE holder_id = 'seed-b-0000001' \gset
SELECT id AS retail FROM account WHERE holder_id = 'seed-r-0050000' \gset

\echo '# EXPLAIN ANALYZE — v2'
\echo
\echo 'Gerado por `make bench` (bench/explain/v2.sql) depois das execuções. Conta empresarial `seed-b-0000001`;'
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
SELECT p.id, p.tx, p.ts, p.kind, p.amt, p.bal, c.code AS category_code, c.name AS category_name, p.dsc, p.cp, p.cpa
FROM (SELECT b.month, e.id, e.tx, e.ts, e.kind, e.amt, e.bal, e.cat, e.dsc, e.cp, e.cpa
      FROM statement_view b
      CROSS JOIN LATERAL jsonb_to_recordset(b.entries)
          AS e(id bigint, tx uuid, ts timestamptz, kind text, amt bigint, bal bigint, cat smallint, dsc text,
               cp text, cpa uuid)
      WHERE b.account_id = :'business'
      AND b.month BETWEEN '2026-01-01' AND '2026-04-01' AND e.ts >= '2026-01-01Z' AND e.ts < '2026-04-01Z'
      ORDER BY b.month DESC, e.ts DESC, e.id DESC LIMIT 51) p
JOIN category c ON c.id = p.cat
ORDER BY p.ts DESC, p.id DESC;
\echo '```'

\echo
\echo '## Q2 — extrato do trimestre, primeira página (pessoa física)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.tx, p.ts, p.kind, p.amt, p.bal, c.code AS category_code, c.name AS category_name, p.dsc, p.cp, p.cpa
FROM (SELECT b.month, e.id, e.tx, e.ts, e.kind, e.amt, e.bal, e.cat, e.dsc, e.cp, e.cpa
      FROM statement_view b
      CROSS JOIN LATERAL jsonb_to_recordset(b.entries)
          AS e(id bigint, tx uuid, ts timestamptz, kind text, amt bigint, bal bigint, cat smallint, dsc text,
               cp text, cpa uuid)
      WHERE b.account_id = :'retail'
      AND b.month BETWEEN '2026-01-01' AND '2026-04-01' AND e.ts >= '2026-01-01Z' AND e.ts < '2026-04-01Z'
      ORDER BY b.month DESC, e.ts DESC, e.id DESC LIMIT 51) p
JOIN category c ON c.id = p.cat
ORDER BY p.ts DESC, p.id DESC;
\echo '```'

\echo
\echo '## Q3 — resumo mensal de 12 meses (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT to_char(s.month, 'YYYY-MM') AS month, c.code, c.name, s.credits, s.debits, s.entries
FROM monthly_summary s JOIN category c ON c.id = s.category_id
WHERE s.account_id = :'business' AND s.month BETWEEN '2025-10-01' AND '2026-09-01'
ORDER BY s.month DESC, c.code;
\echo '```'

\echo
\echo '## Q4 — termo comum (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.tx, p.ts, p.kind, p.amt, p.bal, c.code AS category_code, c.name AS category_name, p.dsc, p.cp, p.cpa
FROM (SELECT b.month, e.id, e.tx, e.ts, e.kind, e.amt, e.bal, e.cat, e.dsc, e.cp, e.cpa
      FROM statement_view b
      CROSS JOIN LATERAL jsonb_to_recordset(b.entries)
          AS e(id bigint, tx uuid, ts timestamptz, kind text, amt bigint, bal bigint, cat smallint, dsc text,
               cp text, cpa uuid)
      WHERE b.account_id = :'business'
      AND b.search_vector @@ plainto_tsquery('simple', 'supermercado')
      AND to_tsvector('simple', e.dsc || ' ' || e.cp) @@ plainto_tsquery('simple', 'supermercado')
      ORDER BY b.month DESC, e.ts DESC, e.id DESC LIMIT 50) p
JOIN category c ON c.id = p.cat
ORDER BY p.ts DESC, p.id DESC;
\echo '```'

\echo
\echo '## Q4 — termo raro (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.tx, p.ts, p.kind, p.amt, p.bal, c.code AS category_code, c.name AS category_name, p.dsc, p.cp, p.cpa
FROM (SELECT b.month, e.id, e.tx, e.ts, e.kind, e.amt, e.bal, e.cat, e.dsc, e.cp, e.cpa
      FROM statement_view b
      CROSS JOIN LATERAL jsonb_to_recordset(b.entries)
          AS e(id bigint, tx uuid, ts timestamptz, kind text, amt bigint, bal bigint, cat smallint, dsc text,
               cp text, cpa uuid)
      WHERE b.account_id = :'business'
      AND b.search_vector @@ plainto_tsquery('simple', 'sushi kaze')
      AND to_tsvector('simple', e.dsc || ' ' || e.cp) @@ plainto_tsquery('simple', 'sushi kaze')
      ORDER BY b.month DESC, e.ts DESC, e.id DESC LIMIT 50) p
JOIN category c ON c.id = p.cat
ORDER BY p.ts DESC, p.id DESC;
\echo '```'

\echo
\echo '## Q4 — contraparte (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.tx, p.ts, p.kind, p.amt, p.bal, c.code AS category_code, c.name AS category_name, p.dsc, p.cp, p.cpa
FROM (SELECT b.month, e.id, e.tx, e.ts, e.kind, e.amt, e.bal, e.cat, e.dsc, e.cp, e.cpa
      FROM statement_view b
      CROSS JOIN LATERAL jsonb_to_recordset(b.entries)
          AS e(id bigint, tx uuid, ts timestamptz, kind text, amt bigint, bal bigint, cat smallint, dsc text,
               cp text, cpa uuid)
      WHERE b.account_id = :'business'
      AND b.counterparties ILIKE '%nonna%' AND e.cp ILIKE '%nonna%'
      ORDER BY b.month DESC, e.ts DESC, e.id DESC LIMIT 50) p
JOIN category c ON c.id = p.cat
ORDER BY p.ts DESC, p.id DESC;
\echo '```'

\echo
\echo '## Q4 — texto + categoria + período (empresa)'
\echo
\echo '```'
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.tx, p.ts, p.kind, p.amt, p.bal, c.code AS category_code, c.name AS category_name, p.dsc, p.cp, p.cpa
FROM (SELECT b.month, e.id, e.tx, e.ts, e.kind, e.amt, e.bal, e.cat, e.dsc, e.cp, e.cpa
      FROM statement_view b
      CROSS JOIN LATERAL jsonb_to_recordset(b.entries)
          AS e(id bigint, tx uuid, ts timestamptz, kind text, amt bigint, bal bigint, cat smallint, dsc text,
               cp text, cpa uuid)
      WHERE b.account_id = :'business'
      AND b.search_vector @@ plainto_tsquery('simple', 'farmácia')
      AND to_tsvector('simple', e.dsc || ' ' || e.cp) @@ plainto_tsquery('simple', 'farmácia')
      AND e.cat = (SELECT id FROM category WHERE code = 'HEALTH')
      AND b.month >= '2026-01-01' AND e.ts >= '2026-01-01Z'
      ORDER BY b.month DESC, e.ts DESC, e.id DESC LIMIT 50) p
JOIN category c ON c.id = p.cat
ORDER BY p.ts DESC, p.id DESC;
\echo '```'

\echo
\echo '## Custo de escrita: um lançamento num bloco cheio (empresa, set/2026)'
\echo
\echo 'No benchmark, os comandos caem no mês corrente, fora da janela do seed: o bloco começa vazio. Num mês já'
\echo 'cheio, cada comando reescreve o bloco inteiro. Medido aqui numa transação desfeita no fim (ADR-0011).'
\echo
SELECT '- Bloco: ' || jsonb_array_length(entries) || ' lançamentos, ' || pg_size_pretty(pg_column_size(entries)::bigint)
       || ' comprimidos'
FROM statement_view WHERE account_id = :'business' AND month = '2026-09-01';
\echo
\echo '```'
BEGIN;
INSERT INTO ledger_entry (transaction_id, account_id, kind, amount, balance_after, category_id, description,
                          counterparty_name, occurred_at)
VALUES (gen_random_uuid(), :'business', 'DEPOSIT', 100, 0, 1, 'Sonda de custo', 'Sonda', '2026-09-15Z')
RETURNING id AS probe \gset
EXPLAIN (ANALYZE, BUFFERS, WAL)
INSERT INTO statement_view AS v (account_id, month, entries, search_vector, counterparties)
SELECT e.account_id, ledger_month(e.occurred_at), jsonb_build_array(statement_entry(e)),
       strip(to_tsvector('simple', e.description || ' ' || e.counterparty_name)), e.counterparty_name
FROM ledger_entry e WHERE e.id = :probe
ON CONFLICT (account_id, month) DO UPDATE SET
    entries = v.entries || excluded.entries,
    search_vector = v.search_vector || excluded.search_vector,
    counterparties = CASE WHEN strpos(v.counterparties, excluded.counterparties) > 0 THEN v.counterparties
                          ELSE v.counterparties || E'\n' || excluded.counterparties END;
ROLLBACK;
\echo '```'
