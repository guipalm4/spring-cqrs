# Ambiente — v3, 2026-10-28 03:53:42 -03

## Execução

- Commit: `61f326d`
- Dataset: 100000 contas do seed (escala 1), 20,265,722 lançamentos
- Capacidade c1 (ADR-0013): REPS=3 DURATION=180s WARMUP=300s PAUSE=20s WRITE_RATE=20 READ_RATES="75 150 300 600 1200"
- Grafana parado durante a medição
- URLs: comandos em http://localhost:8080, consultas em http://localhost:8081
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
| c1-r0075 r1 | início | 4.06 3.89 3.79 | 36% |
| c1-r0075 r1 | fim | 4.95 4.57 4.11 | 36% |
| c1-r0075 r2 | início | 3.56 4.27 4.01 | 36% |
| c1-r0075 r2 | fim | 6.43 4.65 4.18 | 36% |
| c1-r0075 r3 | início | 5.59 4.66 4.21 | 36% |
| c1-r0075 r3 | fim | 3.09 3.77 3.90 | 36% |
| c1-r0150 r1 | início | 2.26 3.49 3.80 | 36% |
| c1-r0150 r1 | fim | 4.64 3.86 3.88 | 37% |
| c1-r0150 r2 | início | 4.24 3.85 3.87 | 37% |
| c1-r0150 r2 | fim | 5.37 4.59 4.16 | 41% |
| c1-r0150 r3 | início | 3.84 4.29 4.07 | 38% |
| c1-r0150 r3 | fim | 4.57 4.23 4.07 | 40% |
| c1-r0300 r1 | início | 5.05 4.37 4.12 | 38% |
| c1-r0300 r1 | fim | 5.36 4.63 4.26 | 38% |
| c1-r0300 r2 | início | 5.56 4.77 4.32 | 37% |
| c1-r0300 r2 | fim | 5.18 5.08 4.55 | 38% |
| c1-r0300 r3 | início | 3.64 4.72 4.43 | 37% |
| c1-r0300 r3 | fim | 3.86 4.60 4.45 | 39% |
| c1-r0600 r1 | início | 3.59 4.47 4.41 | 36% |
| c1-r0600 r1 | fim | 7.41 5.61 4.89 | 41% |
| c1-r0600 r2 | início | 6.18 5.46 4.86 | 37% |
| c1-r0600 r2 | fim | 6.26 6.19 5.32 | 38% |
