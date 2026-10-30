# Ambiente — v3, 2026-10-30 01:40:01 -03

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
- openjdk version "25.0.5" 2026-10-20 LTS

## JVM (projector)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `projector`
- openjdk version "25.0.5" 2026-10-20 LTS

## JVM (query-api)

- JVM_OPTS: `-XX:MaxRAMPercentage=75`
- Profiles do Spring: `query`
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

## MongoDB

- versão 8.0.34
- Cache do WiredTiger: 512 MiB
- Documentos: 2303135 blocos, 8154877 resumos

## Carga do host

Load average (1, 5 e 15 min) e memória livre do macOS, no início e no fim de cada execução (ADR-0013).

| Execução | Momento | Load average | Memória livre |
|---|---|---|---:|
| c2-w0020 r1 | início | 5.12 3.54 3.35 | 40% |
| c2-w0020 r1 | fim | 2.41 3.39 3.36 | 38% |
| c2-w0020 r2 | início | 2.57 3.34 3.34 | 38% |
| c2-w0020 r2 | fim | 5.50 3.86 3.54 | 38% |
| c2-w0020 r3 | início | 4.68 3.79 3.52 | 38% |
| c2-w0020 r3 | fim | 3.54 3.47 3.43 | 38% |
| c2-w0050 r1 | início | 3.06 3.37 3.39 | 39% |
| c2-w0050 r1 | fim | 3.60 3.37 3.37 | 36% |
| c2-w0050 r2 | início | 2.88 3.23 3.32 | 37% |
| c2-w0050 r2 | fim | 1.96 2.73 3.10 | 38% |
| c2-w0050 r3 | início | 1.61 2.58 3.03 | 38% |
| c2-w0050 r3 | fim | 3.91 3.00 3.11 | 40% |
| c2-w0100 r1 | início | 3.19 2.92 3.07 | 40% |
| c2-w0100 r1 | fim | 6.44 4.51 3.71 | 40% |
| c2-w0100 r2 | início | 4.70 4.28 3.65 | 40% |
| c2-w0100 r2 | fim | 2.91 3.62 3.50 | 40% |
| c2-w0100 r3 | início | 2.95 3.60 3.49 | 40% |
| c2-w0100 r3 | fim | 3.52 3.84 3.64 | 40% |
