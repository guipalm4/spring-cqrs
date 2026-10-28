# Ambiente — v1-replica, 2026-10-28 02:09:14 -03

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
| spring-cqrs-app-baseline-1 | spring-cqrs/app-baseline:local | 2.00 | 1024 MiB |
| spring-cqrs-app-baseline-query-1 | spring-cqrs/app-baseline:local | 2.00 | 1024 MiB |
| spring-cqrs-postgres-1 | postgres:18.6 | 2.00 | 2048 MiB |
| spring-cqrs-postgres-exporter-1 | prometheuscommunity/postgres-exporter:v0.20.1 | 0.25 | 128 MiB |
| spring-cqrs-postgres-replica-1 | postgres:18.6 | 2.00 | 2048 MiB |
| spring-cqrs-prometheus-1 | prom/prometheus:v3.15.0 | 0.50 | 512 MiB |

## JVM (app-baseline)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `default`
- openjdk version "25.0.4.1" 2026-08-18 LTS

## JVM (app-baseline-query)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `default`
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

## Réplica

- estado streaming, slot replica
- hot_standby_feedback = on, shared_buffers = 512MB

## Carga do host

Load average (1, 5 e 15 min) e memória livre do macOS, no início e no fim de cada execução (ADR-0013).

| Execução | Momento | Load average | Memória livre |
|---|---|---|---:|
| c1-r0075 r1 | início | 1.62 3.62 5.63 | 44% |
| c1-r0075 r1 | fim | 3.06 3.61 5.25 | 37% |
| c1-r0075 r2 | início | 2.58 3.47 5.16 | 37% |
| c1-r0075 r2 | fim | 2.21 3.02 4.64 | 37% |
| c1-r0075 r3 | início | 1.85 2.87 4.54 | 37% |
| c1-r0075 r3 | fim | 2.64 2.68 4.12 | 37% |
| c1-r0150 r1 | início | 2.82 2.73 4.10 | 37% |
| c1-r0150 r1 | fim | 3.61 3.11 3.98 | 38% |
| c1-r0150 r2 | início | 3.01 3.01 3.92 | 40% |
| c1-r0150 r2 | fim | 4.49 3.50 3.94 | 35% |
| c1-r0150 r3 | início | 4.91 3.68 3.99 | 34% |
| c1-r0150 r3 | fim | 7.78 5.43 4.65 | 37% |
| c1-r0300 r1 | início | 6.86 5.39 4.66 | 39% |
| c1-r0300 r1 | fim | 10.01 7.95 5.93 | 38% |
