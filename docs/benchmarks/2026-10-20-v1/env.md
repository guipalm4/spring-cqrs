# Ambiente — v1, 2026-10-20 12:50:14 -03

## Execução

- Commit: `4c4eff0`
- Dataset: 100000 contas do seed (escala 1), 20,265,722 lançamentos
- Parâmetros: REPS=3 DURATION=180s WARMUP=300s PAUSE=20s WRITE_RATE=20 READ_RATES="25 50 75"
- Gatling 3.16.0 no host: openjdk version "25.0.2" 2026-01-20

## Hardware e runtime

- Host: Apple M1 Pro, 16 GB, macOS 26.6.2
- Docker: Docker Desktop 28.5.1, 8 CPUs, 12529709056 bytes

## Containers

| Container | Imagem | CPUs | Memória |
|---|---|---:|---:|
| spring-cqrs-app-baseline-1 | spring-cqrs/app-baseline:local | 2.00 | 1024 MiB |
| spring-cqrs-grafana-1 | grafana/grafana:13.2.4 | 0.50 | 512 MiB |
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
