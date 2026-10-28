# Ambiente — v3, 2026-10-28 05:29:09 -03

## Execução

- Commit: `3d1e927` (com alterações locais)
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
| c1-r0075 r1 | início | 2.03 2.93 3.91 | 36% |
| c1-r0075 r1 | fim | 5.39 4.78 4.53 | 39% |
| c1-r0075 r2 | início | 4.47 4.61 4.47 | 39% |
| c1-r0075 r2 | fim | 2.54 3.77 4.16 | 36% |
| c1-r0075 r3 | início | 2.54 3.66 4.10 | 35% |
| c1-r0075 r3 | fim | 6.46 4.18 4.17 | 36% |
| c1-r0150 r1 | início | 4.56 3.93 4.08 | 36% |
| c1-r0150 r1 | fim | 7.42 5.29 4.59 | 36% |
| c1-r0150 r2 | início | 6.28 5.23 4.59 | 37% |
| c1-r0150 r2 | fim | 3.42 4.97 4.65 | 38% |
| c1-r0150 r3 | início | 3.34 4.81 4.61 | 37% |
| c1-r0150 r3 | fim | 2.98 4.31 4.46 | 36% |
| c1-r0300 r1 | início | 2.43 4.06 4.37 | 37% |
| c1-r0300 r1 | fim | 6.47 4.58 4.49 | 38% |
| c1-r0300 r2 | início | 5.31 4.48 4.45 | 36% |
| c1-r0300 r2 | fim | 5.67 4.97 4.65 | 35% |
| c1-r0300 r3 | início | 4.31 4.73 4.58 | 36% |
| c1-r0300 r3 | fim | 6.68 5.30 4.81 | 36% |
| c1-r0600 r1 | início | 5.05 5.03 4.73 | 37% |
| c1-r0600 r1 | fim | 7.49 6.04 5.21 | 38% |
