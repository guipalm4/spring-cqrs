# Ambiente — v1, 2026-10-28 00:08:55 -03

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
| spring-cqrs-app-baseline-1 | spring-cqrs/app-baseline:local | 2.00 | 1024 MiB |
| spring-cqrs-postgres-1 | postgres:18.6 | 2.00 | 2048 MiB |
| spring-cqrs-postgres-exporter-1 | prometheuscommunity/postgres-exporter:v0.20.1 | 0.25 | 128 MiB |
| spring-cqrs-prometheus-1 | prom/prometheus:v3.15.0 | 0.50 | 512 MiB |

## JVM (app-baseline)

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
| c1-r0075 r1 | início | 2.29 2.62 2.94 | 59% |
| c1-r0075 r1 | fim | 2.54 2.54 2.83 | 59% |
| c1-r0075 r2 | início | 2.73 2.61 2.85 | 60% |
| c1-r0075 r2 | fim | 1.71 2.52 2.78 | 58% |
| c1-r0075 r3 | início | 1.70 2.46 2.76 | 58% |
| c1-r0075 r3 | fim | 2.02 2.25 2.60 | 58% |
| c1-r0150 r1 | início | 2.44 2.35 2.62 | 59% |
| c1-r0150 r1 | fim | 3.28 2.88 2.80 | 59% |
| c1-r0150 r2 | início | 3.28 2.91 2.82 | 59% |
| c1-r0150 r2 | fim | 2.62 2.79 2.78 | 58% |
| c1-r0150 r3 | início | 2.01 2.64 2.73 | 59% |
| c1-r0150 r3 | fim | 2.37 2.78 2.79 | 58% |
| c1-r0300 r1 | início | 2.07 2.69 2.75 | 59% |
| c1-r0300 r1 | fim | 4.49 3.62 3.14 | 52% |
| c1-r0300 r2 | início | 3.72 3.50 3.11 | 54% |
| c1-r0300 r2 | fim | 3.55 3.70 3.27 | 57% |
| c1-r0300 r3 | início | 2.92 3.54 3.23 | 58% |
| c1-r0300 r3 | fim | 2.92 3.45 3.27 | 55% |
| c1-r0600 r1 | início | 2.50 3.32 3.22 | 57% |
| c1-r0600 r1 | fim | 3.69 3.67 3.40 | 44% |
