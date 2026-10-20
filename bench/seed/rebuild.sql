-- Depois do seed: reconstrói as projeções da v2 a partir do ledger recém-carregado (ADR-0008, ADR-0011).
-- O seed escreve só no modelo de escrita; num banco da v1 não há projeções e o script não faz nada.

SELECT to_regprocedure('rebuild_projections()') IS NOT NULL AS has_projections \gset
\if :has_projections
CALL rebuild_projections();
VACUUM (ANALYZE) statement_view;
VACUUM (ANALYZE) monthly_summary;
\endif
