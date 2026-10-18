# ADR-0005 — Metodologia de benchmark e observabilidade

- **Status:** Aceito
- **Data:** 2026-10-18

## Contexto

O valor do projeto está na comparação. Um benchmark irreproduzível, com baseline fraca ou que esconde o
custo da solução, anula o resto.

## Decisão

### Regras de justiça

1. **Mesmo contrato REST e mesmo script de carga** para v1, v2 e v3.
2. **Mesmo dataset** gerado com semente fixa. Referência: **100 mil contas e 20 milhões de
   lançamentos**, cerca de 24 meses de histórico. O seeder aceita um fator de escala; resultados
   publicados sempre usam a referência, e qualquer outra escala é rotulada como tal.
3. **Limites de CPU/memória por container** declarados no compose; **recursos totais** de cada fase
   reportados junto com os resultados.
4. **Aquecimento** descartado antes da medição; mínimo de 3 execuções por cenário; reportar mediana e
   dispersão.
5. Baseline com índices revisados e `EXPLAIN ANALYZE` publicado.
6. Cada resultado registra hardware, commit, versões e parâmetros da JVM.
7. **O benchmark chama os serviços diretamente, nunca pelo API Gateway do LocalStack.** A latência
   de um emulador não representa a AWS e contaminaria a comparação (ver ADR-0006).

### Cenários

| Cenário | Composição | Responde |
|---|---|---|
| S1 — só escrita | 100% comandos | Referência de escrita |
| S2 — só leitura | Q1–Q4 | Referência de leitura |
| S3 — mista | Escrita constante + leitura crescente | H1, H3 (interferência) |
| S4 — consistência | Escreve e consulta imediatamente | H5 (atraso de projeção) |
| S5 — escala de leitura (v3) | S3 com 1 e com 2 réplicas de `query-api` | H7 (escala independente) |

### Métricas

- Latência p50/p95/p99 por comando e por consulta; vazão.
- Postgres: `pg_stat_statements`, buffer hit ratio, CPU/IO do container.
- Pool: `hikaricp.connections.pending` e `active`.
- v3: atraso da projeção (p50/p99), lag de consumer group.
- Custo: nº de containers, RAM e CPU totais.

### Ferramentas

- **Gatling (Java DSL)** — repositório numa linguagem só; relatórios HTML versionáveis.
- **Micrometer + Prometheus + Grafana** — dashboards versionados como código.
- **OpenTelemetry** (`spring-boot-starter-opentelemetry`) — trace comando → outbox → Kafka → projetor.
- **JFR** (incl. Method Timing do Java 25) para análise pontual.
- Entrada única: `make bench PHASE=v1|v2|v3`; saída em `docs/benchmarks/<data>-<fase>/`.

## Alternativas consideradas

- **k6:** popular e integra bem com Grafana, mas introduz JavaScript; Gatling mantém a coerência do
  portfólio Java.
- **JMH:** microbenchmark, não mede o sistema sob carga — útil só para pontos isolados.

## Consequências

- Benchmark em notebook tem ruído; mitigado com execuções repetidas e limites de recursos, e declarado
  como limitação no README.
- Resultados desfavoráveis à v3 (custo maior que o ganho) são publicados e discutidos.
