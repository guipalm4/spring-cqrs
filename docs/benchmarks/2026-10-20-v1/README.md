# v1 — 2026-10-20: referência do mesmo dia para o controle da #11

A mesma v1 do M2 (desde a tag `m2`, a aplicação só ganhou o profile `split-pools`, desligado aqui), medida de novo no mesmo dia e nas mesmas
condições do [controle com pools separados](../2026-10-20-v1-split/README.md), que é onde está a análise. Ambiente em
[env.md](env.md), números em [summary.md](summary.md), planos em [explain.md](explain.md).

Por que medir de novo: no controle, o S1 (só escrita, que não depende dos pools) ficou ~40% mais rápido que na
[execução de 19/10](../2026-10-19-v1/README.md). Comparar o controle com a v1 de outro dia atribuiria aos pools uma
diferença do ambiente.

## Contra a execução de 19/10

| | 19/10 | 20/10 |
|---|---:|---:|
| Colapsos com leitura ≥ 50/s | 6 de 9 | 3 de 9 |
| Comando segurando a conexão no S1 (mediana) | 13,4 ms | 6,0 ms |
| p95 dos comandos no S1 | 35–53 ms | 22–48 ms |

Mesmo código, mesmo dataset, mesmo ambiente declarado em `env.md`. A diferença de colapsos não é significativa com
3 repetições por cenário (teste exato de Fisher, p = 0,17), mas é do tamanho dos efeitos que as fases procuram. A
conclusão do M2 continua de pé (a leitura derruba a escrita, e o colapso é tudo ou nada), mas a frequência medida
num dia não é um número da v1: é da v1 naquele dia.

Esta execução já mede GC (`gc_pause_total_s`): os três colapsos tiveram 8,6–35,3 s de pausa em 180 s; as execuções
estáveis, no máximo 1,7 s.
