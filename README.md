# spring-cqrs

Ledger financeiro que começa com um modelo único (leitura e escrita no mesmo Postgres) e evolui para
CQRS, medindo a cada fase o que melhora e o que custa.

> **Status:** M2 — evidência do problema: benchmark reproduzível (`make bench`) e H1 confirmada na v1 — a
> leitura degrada a escrita pela fila no pool compartilhado; com 50 consultas/s ou mais, o sistema
> colapsou em 6 de 9 execuções ([resultados](docs/benchmarks/2026-10-19-v1/README.md)).
> Escopo, hipóteses e marcos em [docs/SCOPE.md](docs/SCOPE.md); contrato e dataset no
> [ADR-0008](docs/adr/0008-contrato-v1-e-dataset.md).

## Fases

| Fase | O que muda | Módulo |
|---|---|---|
| v1 — Baseline | — | `app-baseline` |
| v2 — CQRS lógico | Modelos separados, mesmo Postgres | `app-cqrs` |
| v3 — CQRS físico | Stores de leitura separados, sincronização assíncrona | `app-cqrs` + projetores |

## Stack

Java 25 · Spring Boot 4.1 · PostgreSQL 18 · MongoDB · OpenSearch · Kafka · Spring Modulith ·
Testcontainers · Gatling · Prometheus · Grafana. Decisões em [docs/adr](docs/adr).

## Rodando

Pré-requisitos: JDK 25 e um runtime Docker com Compose v2.

```bash
make verify          # build + testes (sobe Postgres via Testcontainers)
make up PHASE=v1     # Postgres, app-baseline, Prometheus, Grafana
make smoke           # confere a saúde da aplicação
make seed SCALE=0.01 # APAGA e carrega um dataset (1 = referência: 100 mil contas, ~20 mi lançamentos)
make bench PHASE=v1  # benchmark da fase no ar (~1 h com SCALE=1); resultados em docs/benchmarks/
make down
```

| Serviço | Endereço |
|---|---|
| Aplicação | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Contrato OpenAPI | http://localhost:8080/v3/api-docs ([versionado](docs/api/openapi.json)) |
| Collection Postman | [docs/api/ledger.postman_collection.json](docs/api/ledger.postman_collection.json) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000/d/ledger (aplicação e Postgres) |

## Benchmark

`make bench` roda a mesma simulação Gatling contra a fase no ar: só escrita (S1), escrita constante com
leitura crescente (S3) e só leitura (S2), com aquecimento descartado e três repetições intercaladas.
Metodologia no [ADR-0005](docs/adr/0005-metodologia-de-benchmark.md), parâmetros e revisão da baseline no
[ADR-0009](docs/adr/0009-revisao-da-baseline-e-parametros-do-benchmark.md), resultados em
[docs/benchmarks](docs/benchmarks).
