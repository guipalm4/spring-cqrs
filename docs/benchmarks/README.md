# Benchmarks

Cada pasta é uma execução de `make bench PHASE=<fase>` sobre o dataset de referência (`make seed SCALE=1`).
Metodologia no [ADR-0005](../adr/0005-metodologia-de-benchmark.md); revisão da baseline e parâmetros no
[ADR-0009](../adr/0009-revisao-da-baseline-e-parametros-do-benchmark.md).

| Execução | Fase | Responde | Leitura |
|---|---|---|---|
| [2026-10-19-v1](2026-10-19-v1/) | v1 | H1 | [análise](2026-10-19-v1/README.md) |

## Conteúdo de uma execução

| Arquivo | O que é |
|---|---|
| `README.md` | Análise e resposta às hipóteses (escrita à mão) |
| `summary.md` | Mediana e faixa das repetições por cenário e requisição; pool e banco por cenário |
| `env.md` | Commit, hardware, runtime, limites dos containers, flags da JVM, configuração do Postgres |
| `explain.md` | `EXPLAIN (ANALYZE, BUFFERS)` de Q1–Q4 depois da carga |
| `runs/<cenário>/r<n>/stats.csv` | Tabela do relatório do Gatling (percentis incluem requisições com KO) |
| `runs/<cenário>/r<n>/pg_stat_statements.csv` | As 25 consultas que mais consumiram tempo no Postgres |
| `runs/<cenário>/r<n>/prometheus.csv` | Pool do Hikari, CPU da aplicação e blocos lidos no Postgres |
| `runs/<cenário>/r<n>/docker-stats.csv` | CPU, memória e I/O por container, amostrados durante a execução |

## Como reproduzir

```bash
make up PHASE=v1
make seed SCALE=1      # ~20 min; APAGA os dados
make bench PHASE=v1    # ~1h20: aquecimento + 3 repetições de S1, S3 (3 taxas) e S2
```

Comparações valem entre fases **na mesma máquina** (ADR-0007): a latência no macOS inclui a VM do Docker.
Feche outros containers antes de medir.
