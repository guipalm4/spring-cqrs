# spring-cqrs

Ledger financeiro que começa com um modelo único (leitura e escrita no mesmo Postgres) e evolui para
CQRS, medindo a cada fase o que melhora e o que custa.

> **Status:** M0 — fundação. Escopo, hipóteses e marcos em [docs/SCOPE.md](docs/SCOPE.md).

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
make down
```

| Serviço | Endereço |
|---|---|
| Aplicação | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
