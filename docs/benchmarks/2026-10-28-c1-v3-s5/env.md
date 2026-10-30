# Ambiente — v3-s5, 2026-10-28 06:27:44 -03

## Execução

- Commit: `3d1e927` (com alterações locais)
- Dataset: 100000 contas do seed (escala 1), 20,265,722 lançamentos
- Capacidade c1 (ADR-0013): REPS=3 DURATION=180s WARMUP=300s PAUSE=20s WRITE_RATE=20 READ_RATES="600 1200"
- Grafana parado durante a medição
- URLs: comandos em http://localhost:8080, consultas em http://localhost:8081,http://localhost:8082
- Gatling 3.16.0 no host: openjdk version "25.0.2" 2026-01-20

## Hardware e runtime

- Host: Apple M1 Pro, 16 GB, macOS 26.6.2
- Docker: Docker Desktop 28.5.1, 8 CPUs, 12529709056 bytes

## Containers

| Container | Imagem | CPUs | Memória |
|---|---|---:|---:|
| spring-cqrs-command-api-1 | spring-cqrs/app-cqrs:local | 2.00 | 1024 MiB |
| spring-cqrs-kafka-1 | apache/kafka:4.2.2 | 1.00 | 1024 MiB |
| spring-cqrs-mongo-1 | mongo:8.0.34 | 2.00 | 2048 MiB |
| spring-cqrs-postgres-1 | postgres:18.6 | 2.00 | 2048 MiB |
| spring-cqrs-postgres-exporter-1 | prometheuscommunity/postgres-exporter:v0.20.1 | 0.25 | 128 MiB |
| spring-cqrs-projector-1 | spring-cqrs/app-cqrs:local | 1.00 | 512 MiB |
| spring-cqrs-prometheus-1 | prom/prometheus:v3.15.0 | 0.50 | 512 MiB |
| spring-cqrs-query-api-1 | spring-cqrs/app-cqrs:local | 2.00 | 1024 MiB |
| spring-cqrs-query-api-2-1 | spring-cqrs/app-cqrs:local | 2.00 | 1024 MiB |

## JVM (command-api)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `command`
- openjdk version "25.0.4.1" 2026-08-18 LTS

## JVM (projector)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `projector`
- openjdk version "25.0.4.1" 2026-08-18 LTS

## JVM (query-api)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `query`
- openjdk version "25.0.4.1" 2026-08-18 LTS

## JVM (query-api-2)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `query`
- openjdk version "25.0.4.1" 2026-08-18 LTS

## Postgres

- PostgreSQL 18.6 (Debian 18.6-1.pgdg13+2) on aarch64-unknown-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit
- effective_cache_size = 196608 8kB
- effective_io_concurrency = 16
- io_method = worker
- maintenance_work_mem = 262144 kB
- max_connections = 100
- random_page_cost = 1.1
- shared_buffers = 65536 8kB
- work_mem = 16384 kB

## MongoDB

- versão 8.0.34
- Cache do WiredTiger: 512 MiB
- Documentos: 2303135 blocos, 8154877 resumos

## Carga do host

Load average (1, 5 e 15 min) e memória livre do macOS, no início e no fim de cada execução (ADR-0013).

| Execução | Momento | Load average | Memória livre |
|---|---|---|---:|
| c1-r0600 r1 | início | 4.69 3.76 3.98 | 35% |
| c1-r0600 r1 | fim | 10.45 8.71 6.16 | 37% |
