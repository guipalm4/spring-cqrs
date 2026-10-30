# Ambiente — v1, 2026-10-29 21:45:17 -03

## Execução

- Commit: `92c9e4a`
- Dataset: 100000 contas do seed (escala 1), 20,265,722 lançamentos
- Capacidade c2 (ADR-0013): REPS=3 DURATION=180s WARMUP=300s PAUSE=20s READ_RATE=75 WRITE_RATES="20 50 100 200 400 800"
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

## Carga do host

Load average (1, 5 e 15 min) e memória livre do macOS, no início e no fim de cada execução (ADR-0013).

| Execução | Momento | Load average | Memória livre |
|---|---|---|---:|
| c2-w0020 r1 | início | 2.18 3.19 5.87 | 40% |
| c2-w0020 r1 | fim | 1.79 2.48 5.04 | 42% |
| c2-w0020 r2 | início | 1.75 2.43 4.96 | 42% |
| c2-w0020 r2 | fim | 2.08 2.26 4.38 | 43% |
| c2-w0020 r3 | início | 1.55 2.13 4.28 | 43% |
| c2-w0020 r3 | fim | 2.59 2.37 3.96 | 42% |
| c2-w0050 r1 | início | 2.54 2.37 3.91 | 46% |
| c2-w0050 r1 | fim | 2.19 2.24 3.55 | 40% |
| c2-w0050 r2 | início | 1.85 2.16 3.49 | 40% |
| c2-w0050 r2 | fim | 2.66 2.69 3.48 | 38% |
| c2-w0050 r3 | início | 2.27 2.60 3.42 | 39% |
| c2-w0050 r3 | fim | 1.92 2.30 3.13 | 41% |
| c2-w0100 r1 | início | 2.02 2.30 3.11 | 37% |
| c2-w0100 r1 | fim | 1.87 2.24 2.92 | 42% |
| c2-w0100 r2 | início | 1.91 2.22 2.90 | 43% |
| c2-w0100 r2 | fim | 2.09 2.85 3.10 | 45% |
| c2-w0100 r3 | início | 2.26 2.86 3.09 | 45% |
| c2-w0100 r3 | fim | 2.11 2.64 2.96 | 45% |
| c2-w0200 r1 | início | 1.94 2.56 2.92 | 46% |
| c2-w0200 r1 | fim | 2.73 2.65 2.88 | 44% |
| c2-w0200 r2 | início | 2.58 2.62 2.87 | 45% |
| c2-w0200 r2 | fim | 3.45 2.81 2.88 | 44% |
| c2-w0200 r3 | início | 3.98 2.96 2.93 | 45% |
| c2-w0200 r3 | fim | 3.50 3.03 2.96 | 44% |
| c2-w0400 r1 | início | 3.13 2.98 2.94 | 46% |
| c2-w0400 r1 | fim | 4.04 3.67 3.25 | 36% |
