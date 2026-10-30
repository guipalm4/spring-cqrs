# Ambiente — v2, 2026-10-30 00:19:37 -03

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
| spring-cqrs-app-cqrs-1 | spring-cqrs/app-cqrs:local | 2.00 | 1024 MiB |
| spring-cqrs-postgres-1 | postgres:18.6 | 2.00 | 2048 MiB |
| spring-cqrs-postgres-exporter-1 | prometheuscommunity/postgres-exporter:v0.20.1 | 0.25 | 128 MiB |
| spring-cqrs-prometheus-1 | prom/prometheus:v3.15.0 | 0.50 | 512 MiB |

## JVM (app-cqrs)

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
| c2-w0020 r1 | início | 1.88 3.08 3.26 | 42% |
| c2-w0020 r1 | fim | 2.27 2.66 3.03 | 42% |
| c2-w0020 r2 | início | 2.08 2.59 3.00 | 41% |
| c2-w0020 r2 | fim | 1.78 2.44 2.87 | 41% |
| c2-w0020 r3 | início | 1.62 2.36 2.83 | 41% |
| c2-w0020 r3 | fim | 1.81 2.17 2.66 | 40% |
| c2-w0050 r1 | início | 2.00 2.19 2.65 | 41% |
| c2-w0050 r1 | fim | 1.13 1.85 2.42 | 39% |
| c2-w0050 r2 | início | 2.93 2.21 2.54 | 38% |
| c2-w0050 r2 | fim | 1.93 2.12 2.44 | 40% |
| c2-w0050 r3 | início | 6.18 3.18 2.82 | 37% |
| c2-w0050 r3 | fim | 1.06 2.20 2.48 | 42% |
| c2-w0100 r1 | início | 1.09 2.11 2.44 | 42% |
| c2-w0100 r1 | fim | 1.93 2.16 2.39 | 44% |
| c2-w0100 r2 | início | 1.66 2.09 2.36 | 44% |
| c2-w0100 r2 | fim | 13.71 5.55 3.61 | 36% |
| c2-w0100 r3 | início | 10.91 5.46 3.63 | 41% |
| c2-w0100 r3 | fim | 2.83 4.25 3.48 | 43% |
| c2-w0200 r1 | início | 1.92 3.92 3.38 | 43% |
| c2-w0200 r1 | fim | 4.55 3.97 3.48 | 41% |
| c2-w0200 r2 | início | 3.98 3.88 3.46 | 46% |
| c2-w0200 r2 | fim | 3.14 3.65 3.45 | 42% |
| c2-w0200 r3 | início | 3.02 3.60 3.44 | 43% |
| c2-w0200 r3 | fim | 2.33 2.88 3.15 | 44% |
| c2-w0400 r1 | início | 1.79 2.71 3.08 | 43% |
| c2-w0400 r1 | fim | 4.92 4.34 3.73 | 40% |
| c2-w0400 r2 | início | 3.69 4.10 3.66 | 44% |
| c2-w0400 r2 | fim | 2.66 3.57 3.53 | 46% |
| c2-w0400 r3 | início | 2.39 3.44 3.49 | 48% |
| c2-w0400 r3 | fim | 3.28 3.67 3.59 | 48% |
| c2-w0800 r1 | início | 3.15 3.63 3.58 | 49% |
| c2-w0800 r1 | fim | 5.17 4.39 3.90 | 49% |
| c2-w0800 r2 | início | 4.16 4.22 3.86 | 49% |
| c2-w0800 r2 | fim | 5.62 4.84 4.18 | 35% |
