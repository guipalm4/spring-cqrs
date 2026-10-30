#!/usr/bin/env bash
# Série temporal de uma execução, a partir do Prometheus: bench/timeseries.sh <dir> <início> <fim> (epoch, s).
# Gera <dir>/timeseries.csv com um ponto a cada 5 s (o intervalo de coleta); taxas e médias sobre janelas de 15 s.
# Chamado pelo run.sh ao fim de cada execução. O Prometheus não tem volume: a série só existe enquanto o container
# dura, então ela tem que ser exportada na hora.
#
# Latências medidas na aplicação: incluem a espera pelo pool, mas não a fila antes do Tomcat nem a rede até o
# Gatling. A latência do cliente fica no stats.csv.
set -euo pipefail

dir=$1 start=$2 end=$3
PROMETHEUS=${PROMETHEUS:-http://localhost:9090}
STEP=5

api='uri!~"/actuator.*"'
columns=(
    "commands_rps|sum(rate(http_server_requests_seconds_count{method=\"POST\",$api}[15s]))"
    "queries_rps|sum(rate(http_server_requests_seconds_count{method=\"GET\",$api}[15s]))"
    "errors_rps|sum(rate(http_server_requests_seconds_count{status=~\"5..\",$api}[15s]))"
    "commands_p95_ms|1000 * histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{method=\"POST\",$api}[15s])))"
    "queries_p95_ms|1000 * histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{method=\"GET\",$api}[15s])))"
    "hikari_pending|sum(hikaricp_connections_pending)"
    "hikari_active|sum(hikaricp_connections_active)"
    "hikari_acquire_avg_ms|1000 * sum(rate(hikaricp_connections_acquire_seconds_sum[15s])) / sum(rate(hikaricp_connections_acquire_seconds_count[15s]))"
    "hikari_usage_avg_ms|1000 * sum(rate(hikaricp_connections_usage_seconds_sum[15s])) / sum(rate(hikaricp_connections_usage_seconds_count[15s]))"
    # Só no controle da #11 (pools "commands" e "queries", ADR-0010); vazias com um pool compartilhado.
    "commands_pool_pending|sum(hikaricp_connections_pending{pool=\"commands\"})"
    "commands_pool_acquire_avg_ms|1000 * sum(rate(hikaricp_connections_acquire_seconds_sum{pool=\"commands\"}[15s])) / sum(rate(hikaricp_connections_acquire_seconds_count{pool=\"commands\"}[15s]))"
    "queries_pool_pending|sum(hikaricp_connections_pending{pool=\"queries\"})"
    "pg_blks_read_per_s|sum(rate(pg_stat_database_blks_read{datname=\"ledger\"}[15s]))"
    "app_cpu|avg(process_cpu_usage)"
    "app_heap_mib|sum(jvm_memory_used_bytes{area=\"heap\"}) / 1048576"
    # Fração do tempo com a JVM parada em GC: a pausa para todas as requisições, com ou sem conexão (ADR-0010). Uma
    # pausa mais longa que a janela derruba a coleta e fica vazia aqui; o total por execução está no prometheus.csv.
    "gc_pause_ratio|sum(rate(jvm_gc_pause_seconds_sum[15s]))"
    # v3 (ADR-0012) e v1-replica (ADR-0013): a JVM dos comandos separada da das consultas, e o atraso da projeção;
    # vazias nas outras fases (na v1 e na v2, as duas JVMs são a mesma, e as colunas de comandos repetem as totais).
    "command_heap_mib|sum(jvm_memory_used_bytes{area=\"heap\",instance=~\"(command-api|app-baseline|app-baseline-split|app-baseline-big|app-cqrs):8080\"}) / 1048576"
    "command_gc_pause_ratio|sum(rate(jvm_gc_pause_seconds_sum{instance=~\"(command-api|app-baseline|app-baseline-split|app-baseline-big|app-cqrs):8080\"}[15s]))"
    "query_heap_mib|sum(jvm_memory_used_bytes{area=\"heap\",instance=~\"(query-api.*|app-baseline-query):8080\"}) / 1048576"
    "query_gc_pause_ratio|sum(rate(jvm_gc_pause_seconds_sum{instance=~\"(query-api.*|app-baseline-query):8080\"}[15s]))"
    "projection_lag_p95_ms|1000 * histogram_quantile(0.95, sum by (le) (rate(ledger_projection_lag_seconds_bucket[15s])))"
    "outbox_backlog|sum(ledger_outbox_backlog)"
)

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

header=t
for i in "${!columns[@]}"; do
    header+=,${columns[$i]%%|*}
    curl -fsS --get "$PROMETHEUS/api/v1/query_range" --data-urlencode "query=${columns[$i]#*|}" \
        --data-urlencode "start=$start" --data-urlencode "end=$end" --data-urlencode "step=$STEP" \
        | jq -r '.data.result[0].values[]? | "\(.[0]) \(.[1])"' > "$tmp/$i"
done

# Uma linha por instante; métrica sem valor no instante (ou NaN, sem requisições na janela) fica vazia.
{
    echo "$header"
    LC_ALL=C awk -v start="$start" -v end="$end" -v step="$STEP" -v ncols="${#columns[@]}" '
        { n = split(FILENAME, path, "/"); if ($2 != "NaN") value[path[n], $1] = sprintf("%.2f", $2) }
        END {
            for (t = start; t <= end; t += step) {
                line = t - start
                for (c = 0; c < ncols; c++) line = line "," value[c, t]
                print line
            }
        }' "$tmp"/*
} > "$dir/timeseries.csv"
