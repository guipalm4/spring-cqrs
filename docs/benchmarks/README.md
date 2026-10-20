# Benchmarks

Cada pasta é uma execução de `make bench PHASE=<fase>` sobre o dataset de referência (`make seed SCALE=1`).
Metodologia no [ADR-0005](../adr/0005-metodologia-de-benchmark.md); revisão da baseline e parâmetros no
[ADR-0009](../adr/0009-revisao-da-baseline-e-parametros-do-benchmark.md).

| Execução | Fase | Responde | Leitura |
|---|---|---|---|
| [2026-10-19-v1](2026-10-19-v1/) | v1 | H1 | [análise](2026-10-19-v1/README.md) |
| [2026-10-20-v1](2026-10-20-v1/) | v1 | Referência do mesmo dia para a #11 | [nota](2026-10-20-v1/README.md) |
| [2026-10-20-v1-split](2026-10-20-v1-split/) | v1, pools separados | Controle da #11 (H3) | [análise](2026-10-20-v1-split/README.md) |

## Conteúdo de uma execução

| Arquivo | O que é |
|---|---|
| `README.md` | Análise e resposta às hipóteses (escrita à mão) |
| `summary.md` | Estado de cada execução (estável ou colapso), colapsos por cenário e gráfico do p95 dos comandos; mediana e faixa das repetições por requisição; pool e banco |
| `env.md` | Commit, hardware, runtime, limites dos containers, flags da JVM, configuração do Postgres |
| `explain.md` | `EXPLAIN (ANALYZE, BUFFERS)` de Q1–Q4 depois da carga |
| `runs/<cenário>/r<n>/stats.csv` | Tabela do relatório do Gatling (percentis incluem requisições com KO) |
| `runs/<cenário>/r<n>/pg_stat_statements.csv` | As instruções que mais consumiram tempo no Postgres (25 na v1, 50 a partir da #11) |
| `runs/<cenário>/r<n>/prometheus.csv` | Pool do Hikari (total e, a partir da #11, por pool), CPU da aplicação e blocos lidos no Postgres |
| `runs/<cenário>/r<n>/docker-stats.csv` | CPU, memória e I/O por container, amostrados durante a execução |
| `runs/<cenário>/r<n>/timeseries.csv` | Série a cada 5 s, do Prometheus: vazão, erros e p95 (na aplicação) de comandos e consultas, fila e uso do pool, blocos lidos, CPU e heap |

## Como reproduzir

```bash
make up PHASE=v1
make seed SCALE=1      # ~20 min; APAGA os dados
make bench PHASE=v1    # ~1h20: aquecimento + 3 repetições de S1, S3 (3 taxas) e S2
```

O controle da [#11](https://github.com/guipalm4/spring-cqrs/issues/11) (v1 com pools separados,
[ADR-0010](../adr/0010-controle-pools-separados.md)) usa `PHASE=v1-split` nos três comandos.

Comparações valem entre execuções **do mesmo dia, na mesma máquina** (ADR-0007, ADR-0010): a latência no macOS
inclui a VM do Docker, e a mesma v1 variou de 6 para 3 colapsos em 9 entre dois dias.
Feche outros containers antes de medir.
