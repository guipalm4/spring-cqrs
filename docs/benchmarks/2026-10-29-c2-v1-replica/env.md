# Ambiente — v1-replica, 2026-10-29 23:48:58 -03

## Execução

- Commit: `92c9e4a`
- Dataset: 100000 contas do seed (escala 1), 20,265,722 lançamentos
- Capacidade c2 (ADR-0013): REPS=3 DURATION=180s WARMUP=300s PAUSE=20s READ_RATE=75 WRITE_RATES="20 50 100 200 400 800"
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
- openjdk version "25.0.5" 2026-10-20 LTS

## JVM (app-baseline-query)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `default`
- openjdk version "25.0.5" 2026-10-20 LTS

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
| c2-w0020 r1 | início | 4.54 4.27 3.78 | 33% |
| c2-w0020 r1 | fim | 4.59 4.60 4.04 | 41% |
| c2-w0020 r2 | início | 4.21 4.50 4.01 | 40% |
| c2-w0020 r2 | fim | 4.90 4.58 4.13 | 35% |
| c2-w0020 r3 | início | 4.07 4.42 4.08 | 34% |
| c2-w0020 r3 | fim | 3.74 4.16 4.04 | 40% |
