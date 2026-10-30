# Ambiente — v1-big, 2026-10-29 22:48:57 -03

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
| spring-cqrs-app-baseline-big-1 | spring-cqrs/app-baseline:local | 4.00 | 2048 MiB |
| spring-cqrs-postgres-1 | postgres:18.6 | 4.00 | 4096 MiB |
| spring-cqrs-postgres-exporter-1 | prometheuscommunity/postgres-exporter:v0.20.1 | 0.25 | 128 MiB |
| spring-cqrs-prometheus-1 | prom/prometheus:v3.15.0 | 0.50 | 512 MiB |

## JVM (app-baseline-big)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `default`
- openjdk version "25.0.5" 2026-10-20 LTS

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
| c2-w0020 r1 | início | 3.50 5.45 4.90 | 42% |
| c2-w0020 r1 | fim | 3.02 4.19 4.46 | 43% |
| c2-w0020 r2 | início | 2.65 4.04 4.40 | 44% |
| c2-w0020 r2 | fim | 2.13 3.21 3.97 | 42% |
| c2-w0020 r3 | início | 2.11 3.13 3.92 | 42% |
| c2-w0020 r3 | fim | 2.04 2.60 3.53 | 36% |
| c2-w0050 r1 | início | 1.67 2.48 3.47 | 39% |
| c2-w0050 r1 | fim | 2.61 2.69 3.37 | 40% |
| c2-w0050 r2 | início | 2.22 2.59 3.32 | 40% |
| c2-w0050 r2 | fim | 2.72 2.96 3.35 | 47% |
| c2-w0050 r3 | início | 2.32 2.85 3.30 | 48% |
| c2-w0050 r3 | fim | 1.88 2.46 3.05 | 40% |
| c2-w0100 r1 | início | 5.92 3.37 3.36 | 37% |
| c2-w0100 r1 | fim | 2.05 2.91 3.19 | 39% |
| c2-w0100 r2 | início | 1.77 2.79 3.14 | 42% |
| c2-w0100 r2 | fim | 2.85 2.82 3.08 | 41% |
| c2-w0100 r3 | início | 2.62 2.77 3.05 | 45% |
| c2-w0100 r3 | fim | 2.86 2.86 3.04 | 43% |
| c2-w0200 r1 | início | 3.04 2.93 3.06 | 40% |
| c2-w0200 r1 | fim | 3.82 3.22 3.14 | 43% |
| c2-w0200 r2 | início | 3.09 3.10 3.10 | 40% |
| c2-w0200 r2 | fim | 7.72 4.83 3.78 | 34% |
| c2-w0200 r3 | início | 5.94 4.65 3.75 | 41% |
| c2-w0200 r3 | fim | 6.75 5.06 4.06 | 38% |
