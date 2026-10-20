# v1 — 2026-10-20 (tarde): referência do mesmo dia para a v2

A mesma v1 do M2, medida logo depois da [v2](../2026-10-20-v2/README.md), que é onde está a análise. Antes dela, o
volume do Postgres foi recriado (`make clean`, exigido pela troca de fase) e o dataset recarregado. Ambiente em
[env.md](env.md), números em [summary.md](summary.md), planos em [explain.md](explain.md).

## Contra as outras execuções da v1

| | 19/10 | 20/10, manhã | 20/10, tarde |
|---|---:|---:|---:|
| Colapsos com leitura ≥ 50/s | 6 de 9 | 3 de 9 | 0 de 9 |
| Tempo por bloco lido fora do cache (`s2`) | 1,8–4,1 ms | 0,31–2,2 ms | 0,17–0,21 ms |
| Tempo médio com a conexão (`s2`) | 120 ms | 23 ms | 7,5 ms |

Mesmo código, mesmo dataset, mesmo ambiente declarado em `env.md`. O que mudou foi a latência de ler uma página que
não está no `shared_buffers` (`read_ms / shared_blks_read` no `pg_stat_statements`), e com ela a frequência de colapso.
A causa está fora do Postgres (disco da VM do Docker, cache do macOS) e não foi isolada. A diferença conhecida em
relação à manhã é que, naquela execução, o container do Postgres estava no ar desde a véspera, com o volume reaproveitado
entre vários seeds. O colapso do M2 continua real, mas depende de leituras lentas.
