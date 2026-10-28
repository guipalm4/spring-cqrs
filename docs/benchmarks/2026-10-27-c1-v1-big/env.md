# Ambiente — v1-big, 2026-10-28 01:03:32 -03

## Execução

- Commit: `61f326d`
- Dataset: 100000 contas do seed (escala 1), 20,265,722 lançamentos
- Capacidade c1 (ADR-0013): REPS=3 DURATION=180s WARMUP=300s PAUSE=20s WRITE_RATE=20 READ_RATES="75 150 300 600 1200"
- Grafana parado durante a medição
- URLs: comandos em http://localhost:8080, consultas em http://localhost:8080
- Gatling 3.16.0 no host: openjdk version "25.0.2" 2026-01-20

## Hardware e runtime

- Host: Apple M1 Pro, 16 GB, macOS 26.6.2
- Docker: Docker Desktop 28.5.1, 8 CPUs, 12529709056 bytes

## Containers

| Container | Imagem | CPUs | Memória |
|---|---|---:|---:|
| spring-cqrs-app-baseline-big-1 | spring-cqrs/app-baseline:local | 4.00 | 2048 MiB |
| spring-cqrs-postgres-1 | postgres:18.6 | 4.00 | 4096 MiB |
| spring-cqrs-postgres-exporter-1 | prometheuscommunity/postgres-exporter:v0.20.1 | 0.25 | 128 MiB |
| spring-cqrs-prometheus-1 | prom/prometheus:v3.15.0 | 0.50 | 512 MiB |

## JVM (app-baseline-big)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `default`
- openjdk version "25.0.4.1" 2026-08-18 LTS

## Postgres

- PostgreSQL 18.6 (Debian 18.6-1.pgdg13+2) on aarch64-unknown-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit
- effective_cache_size = 393216 8kB
- effective_io_concurrency = 16
- io_method = worker
- maintenance_work_mem = 262144 kB
- max_connections = 100
- random_page_cost = 1.1
- shared_buffers = 131072 8kB
- work_mem = 16384 kB

## Carga do host

Load average (1, 5 e 15 min) e memória livre do macOS, no início e no fim de cada execução (ADR-0013).

| Execução | Momento | Load average | Memória livre |
|---|---|---|---:|
| c1-r0075 r1 | início | 2.24 4.59 4.18 | 41% |
| c1-r0075 r1 | fim | 2.23 3.56 3.83 | 43% |
| c1-r0075 r2 | início | 2.66 3.56 3.82 | 36% |
| c1-r0075 r2 | fim | 2.10 2.96 3.52 | 37% |
| c1-r0075 r3 | início | 1.85 2.83 3.46 | 37% |
| c1-r0075 r3 | fim | 3.34 3.17 3.47 | 39% |
| c1-r0150 r1 | início | 2.46 2.98 3.40 | 42% |
| c1-r0150 r1 | fim | 8.49 4.66 3.96 | 36% |
| c1-r0150 r2 | início | 7.45 4.68 3.98 | 35% |
| c1-r0150 r2 | fim | 4.30 4.75 4.17 | 50% |
| c1-r0150 r3 | início | 3.42 4.51 4.10 | 42% |
| c1-r0150 r3 | fim | 2.52 3.68 3.84 | 43% |
| c1-r0300 r1 | início | 2.18 3.51 3.77 | 42% |
| c1-r0300 r1 | fim | 5.78 4.46 4.12 | 40% |
| c1-r0300 r2 | início | 5.45 4.47 4.13 | 42% |
| c1-r0300 r2 | fim | 4.11 4.60 4.27 | 43% |
| c1-r0300 r3 | início | 3.44 4.41 4.21 | 35% |
| c1-r0300 r3 | fim | 6.38 4.89 4.40 | 42% |
| c1-r0600 r1 | início | 5.07 4.68 4.34 | 41% |
| c1-r0600 r1 | fim | 14.90 11.15 7.30 | 36% |
| c1-r0600 r2 | início | 9.44 10.23 7.12 | 34% |
| c1-r0600 r2 | fim | 13.59 13.63 9.48 | 33% |
| c1-r0600 r3 | início | 10.24 12.86 9.32 | 38% |
| c1-r0600 r3 | fim | 12.21 14.83 11.23 | 32% |
