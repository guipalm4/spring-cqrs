# Ambiente — v2, 2026-10-28 02:54:55 -03

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
| spring-cqrs-app-cqrs-1 | spring-cqrs/app-cqrs:local | 2.00 | 1024 MiB |
| spring-cqrs-postgres-1 | postgres:18.6 | 2.00 | 2048 MiB |
| spring-cqrs-postgres-exporter-1 | prometheuscommunity/postgres-exporter:v0.20.1 | 0.25 | 128 MiB |
| spring-cqrs-prometheus-1 | prom/prometheus:v3.15.0 | 0.50 | 512 MiB |

## JVM (app-cqrs)

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

## Carga do host

Load average (1, 5 e 15 min) e memória livre do macOS, no início e no fim de cada execução (ADR-0013).

| Execução | Momento | Load average | Memória livre |
|---|---|---|---:|
| c1-r0075 r1 | início | 2.75 3.15 4.01 | 36% |
| c1-r0075 r1 | fim | 3.58 3.66 4.08 | 39% |
| c1-r0075 r2 | início | 3.20 3.57 4.04 | 40% |
| c1-r0075 r2 | fim | 4.64 4.07 4.15 | 37% |
| c1-r0075 r3 | início | 3.95 3.96 4.11 | 38% |
| c1-r0075 r3 | fim | 2.79 3.49 3.88 | 43% |
| c1-r0150 r1 | início | 2.66 3.41 3.84 | 42% |
| c1-r0150 r1 | fim | 3.84 3.56 3.81 | 37% |
| c1-r0150 r2 | início | 3.23 3.44 3.76 | 38% |
| c1-r0150 r2 | fim | 3.02 3.39 3.68 | 40% |
| c1-r0150 r3 | início | 2.82 3.31 3.64 | 35% |
| c1-r0150 r3 | fim | 3.13 3.21 3.53 | 37% |
| c1-r0300 r1 | início | 2.32 3.01 3.46 | 38% |
| c1-r0300 r1 | fim | 4.09 3.52 3.57 | 40% |
| c1-r0300 r2 | início | 4.05 3.58 3.60 | 35% |
| c1-r0300 r2 | fim | 2.55 3.09 3.38 | 40% |
| c1-r0300 r3 | início | 3.58 3.28 3.44 | 38% |
| c1-r0300 r3 | fim | 3.49 3.35 3.44 | 37% |
| c1-r0600 r1 | início | 2.78 3.20 3.38 | 39% |
| c1-r0600 r1 | fim | 6.01 4.91 4.07 | 37% |
