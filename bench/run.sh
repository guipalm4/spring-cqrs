#!/usr/bin/env bash
# Benchmark de uma fase: make bench PHASE=v1. Metodologia no ADR-0005; parâmetros no ADR-0009.
#
# Pré-requisitos: a fase no ar (make up PHASE=...) e o dataset carregado (make seed SCALE=1).
# Na v3, comandos vão para a command-api (:8080) e consultas para a query-api (:8081; no S5, :8081 e :8082), direto
# (ADR-0005, ADR-0012). Nas outras fases, as duas URLs são o mesmo app.
# Saída: docs/benchmarks/<data>-<fase>/ com env.md, summary.md e runs/<cenário>/r<n>/ (estatísticas do Gatling,
# pg_stat_statements, docker stats, métricas do Prometheus e série temporal). Os relatórios HTML completos ficam
# em bench/target/gatling/.
#
# Cenários (mesma simulação, taxas diferentes):
#   s1        só escrita, WRITE_RATE comandos/s
#   s3-r<N>   escrita constante + N consultas/s, para cada N em READ_RATES (H1)
#   s2        só leitura, na maior taxa de READ_RATES
# As repetições são intercaladas (r1 de todos os cenários, depois r2...), para a deriva do cache e do
# autovacuum se espalhar entre os cenários em vez de cair toda num deles.
set -euo pipefail

cd "$(dirname "$0")/.."

PHASE=${PHASE:-v1}
REPS=${REPS:-3}
DURATION=${DURATION:-180}
WARMUP=${WARMUP:-300}
PAUSE=${PAUSE:-20}
WRITE_RATE=${WRITE_RATE:-20}
READ_RATES=${READ_RATES:-"25 50 75"}
COMMANDS_URL=${COMMANDS_URL:-http://localhost:8080}
case $PHASE in
    v3) default_queries=http://localhost:8081 ;;
    v3-s5) default_queries=http://localhost:8081,http://localhost:8082 ;;
    *) default_queries=$COMMANDS_URL ;;
esac
QUERIES_URLS=${QUERIES_URLS:-$default_queries}
PROMETHEUS=${PROMETHEUS:-http://localhost:9090}

COMPOSE=(docker compose -f infra/compose.yaml --profile "$PHASE")
ACCOUNTS=$PWD/bench/target/accounts.csv

psql_() { "${COMPOSE[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U ledger -d ledger -qAt "$@"; }
log() { printf '\n[bench %s] %s\n' "$(date +%T)" "$*"; }

# --- Pré-condições ---------------------------------------------------------------------------------------------
for url in "$COMMANDS_URL" ${QUERIES_URLS//,/ }; do
    curl -fsS "$url/actuator/health" > /dev/null || { echo "$url fora do ar: make up PHASE=$PHASE" >&2; exit 1; }
done
has_mongo=$("${COMPOSE[@]}" ps --format '{{.Service}}' | grep -qx mongo && echo 1 || true)
# Com o tracing ligado (make up TRACE=1), as JVMs da v3 pagam um custo que a v1 e a v2 não pagaram (ADR-0012).
if docker compose -f infra/compose.yaml --profile trace ps --format '{{.Service}}' | grep -qx tempo; then
    echo "o Tempo está no ar (TRACE=1): suba a fase sem TRACE para medir" >&2
    exit 1
fi
seed_accounts=$(psql_ -c "SELECT count(*) FROM account WHERE holder_id LIKE 'seed-%'")
[[ $seed_accounts -gt 0 ]] || { echo "dataset vazio: make seed SCALE=1" >&2; exit 1; }
scale=$(awk -v n="$seed_accounts" 'BEGIN { printf "%g", n / 100000 }')
OUT=${OUT:-docs/benchmarks/$(date +%F)-$PHASE$([[ $scale == 1 ]] || echo "-scale$scale")}
if [[ -e $OUT ]]; then
    echo "$OUT já existe; apague ou passe OUT=..." >&2
    exit 1
fi
mkdir -p "$OUT/runs" bench/target

log "exportando contas do seed ($seed_accounts, escala $scale)"
psql_ -c "\copy (SELECT id, CASE WHEN holder_id LIKE 'seed-b-%' THEN 'business' ELSE 'retail' END
                 FROM account WHERE holder_id LIKE 'seed-%' ORDER BY holder_id) TO STDOUT WITH CSV HEADER" > "$ACCOUNTS"

log "compilando a simulação"
./mvnw -B -ntp -q -f bench/pom.xml test-compile

# --- Ambiente (ADR-0005, regra 6) ------------------------------------------------------------------------------
apps=$("${COMPOSE[@]}" ps --format '{{.Service}}' | grep -E '^app-|-api(-2)?$|^projector$' | sort)
{
    echo "# Ambiente — $PHASE, $(date '+%F %T %Z')"
    echo
    echo "## Execução"
    echo
    echo "- Commit: \`$(git rev-parse --short HEAD)\`$(git diff --quiet HEAD -- . ':!docs/benchmarks' || echo ' (com alterações locais)')"
    echo "- Dataset: $seed_accounts contas do seed (escala $scale), $(psql_ -c "SELECT to_char(count(*), 'FM999G999G999') FROM ledger_entry") lançamentos"
    echo "- Parâmetros: REPS=$REPS DURATION=${DURATION}s WARMUP=${WARMUP}s PAUSE=${PAUSE}s WRITE_RATE=$WRITE_RATE READ_RATES=\"$READ_RATES\""
    echo "- URLs: comandos em $COMMANDS_URL, consultas em $QUERIES_URLS"
    echo "- Gatling $(sed -n 's:.*<gatling.version>\(.*\)</gatling.version>.*:\1:p' bench/pom.xml) no host: $(java -version 2>&1 | head -1)"
    echo
    echo "## Hardware e runtime"
    echo
    echo "- Host: $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m), $(( $(sysctl -n hw.memsize 2>/dev/null || echo 0) / 1073741824 )) GB, $(sw_vers -productVersion 2>/dev/null | sed 's/^/macOS /' || uname -sr)"
    echo "- Docker: $(docker info --format '{{.OperatingSystem}} {{.ServerVersion}}, {{.NCPU}} CPUs, {{.MemTotal}} bytes')"
    echo
    echo "## Containers"
    echo
    echo "| Container | Imagem | CPUs | Memória |"
    echo "|---|---|---:|---:|"
    for id in $("${COMPOSE[@]}" ps -q); do
        docker inspect -f '{{.Name}} {{.Config.Image}} {{.HostConfig.NanoCpus}} {{.HostConfig.Memory}}' "$id" \
            | LC_ALL=C awk '{ printf "| %s | %s | %.2f | %d MiB |\n", substr($1, 2), $2, $3 / 1e9, $4 / 1048576 }'
    done
    echo
    for app in $apps; do
        echo "## JVM ($app)"
        echo
        echo "- JVM_OPTS: \`$("${COMPOSE[@]}" exec -T "$app" sh -c 'echo "$JVM_OPTS"')\`"
        echo "- Profiles do Spring: \`$("${COMPOSE[@]}" exec -T "$app" sh -c 'echo "${SPRING_PROFILES_ACTIVE:-default}"')\`"
        echo "- $("${COMPOSE[@]}" exec -T "$app" java -version 2>&1 | head -1)"
        echo
    done
    echo "## Postgres"
    echo
    echo "- $(psql_ -c 'SELECT version()')"
    psql_ -c "SELECT '- ' || name || ' = ' || setting || coalesce(' ' || unit, '') FROM pg_settings
              WHERE name IN ('shared_buffers', 'effective_cache_size', 'work_mem', 'maintenance_work_mem',
                             'random_page_cost', 'max_connections', 'io_method', 'effective_io_concurrency')
              ORDER BY name"
    if [[ -n $has_mongo ]]; then
        echo
        echo "## MongoDB"
        echo
        echo "- $("${COMPOSE[@]}" exec -T mongo mongosh --quiet --eval 'db.version()' | sed 's/^/versão /')"
        echo "- Cache do WiredTiger: $("${COMPOSE[@]}" exec -T mongo mongosh --quiet --eval \
            'db.serverStatus().wiredTiger.cache["maximum bytes configured"] / 1048576') MiB"
        echo "- Documentos: $("${COMPOSE[@]}" exec -T mongo mongosh ledger --quiet --eval \
            'db.statement_view.estimatedDocumentCount() + " blocos, " + db.monthly_summary.estimatedDocumentCount() + " resumos"')"
    fi
} > "$OUT/env.md"

# --- Execuções -------------------------------------------------------------------------------------------------
gatling() { # writeRate readRate duration seed
    ./mvnw -B -ntp -q -f bench/pom.xml gatling:test -DcommandsUrl="$COMMANDS_URL" -DqueriesUrls="$QUERIES_URLS" \
        -Daccounts="$ACCOUNTS" -DwriteRate="$1" -DreadRate="$2" -Dduration="$3" -Dseed="$4"
}

# Contadores do cache do WiredTiger (cumulativos desde a partida do Mongo): a diferença entre o fim e o início da
# execução é o equivalente do shared_blks_read e do read_ms do Postgres.
mongo_cache() {
    "${COMPOSE[@]}" exec -T mongo mongosh --quiet --eval '
        const c = db.serverStatus().wiredTiger.cache;
        print([c["pages read into cache"], c["bytes read into cache"],
               c["application threads page read from disk to cache count"],
               c["application threads page read from disk to cache time (usecs)"]].join(","))'
}

prometheus() { # expressão -> valor no instante atual
    curl -fsS --get "$PROMETHEUS/api/v1/query" --data-urlencode "query=$1" \
        | jq -r '.data.result[0].value[1] // "NaN"'
}

prometheus_by_pool() { # nome expressão-agrupada-por-instância-e-pool -> "nome_<pool>@<instância>,valor" por pool
    # Na v3, command-api e query-api têm um pool cada, com o mesmo nome: a instância os separa.
    curl -fsS --get "$PROMETHEUS/api/v1/query" --data-urlencode "query=$2" \
        | jq -r --arg name "$1" '.data.result[] | "\($name)_\(.metric.pool)@\(.metric.instance | sub(":8080$"; "")),\(.value[1])"'
}

prometheus_by_instance() { # nome expressão-agrupada-por-instância -> "nome_<instância>,valor" por JVM
    curl -fsS --get "$PROMETHEUS/api/v1/query" --data-urlencode "query=$2" \
        | jq -r --arg name "$1" '.data.result[] | "\($name)_\(.metric.instance | sub(":8080$"; "")),\(.value[1])"'
}

run() { # nome writeRate readRate rep
    local name=$1 write=$2 read=$3 rep=$4
    local dir=$OUT/runs/$name/r$rep
    mkdir -p "$dir"
    printf 'writeRate=%s\nreadRate=%s\nduration=%s\n' "$write" "$read" "$DURATION" > "$OUT/runs/$name/scenario.properties"

    log "$name r$rep: escrita $write/s, leitura $read/s, ${DURATION}s"
    psql_ -c 'CHECKPOINT' -c 'SELECT pg_stat_statements_reset()' > /dev/null
    sleep "$PAUSE"

    (while true; do
        docker stats --no-stream --format "{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.BlockIO}}" $("${COMPOSE[@]}" ps -q) \
            | sed "s/^/$(date +%s),/"
    done) > "$dir/docker-stats.csv" &
    local sampler=$! start mongo_before=""
    start=$(date +%s)
    [[ -z $has_mongo ]] || mongo_before=$(mongo_cache)

    # Semente por repetição: cada repetição sorteia outras contas, e a mesma repetição sorteia as mesmas
    # contas em todas as fases.
    gatling "$write" "$read" "$DURATION" $((100 + rep)) > "$dir/gatling.log" 2>&1 || {
        kill "$sampler"; tail -40 "$dir/gatling.log" >&2; exit 1; }
    kill "$sampler"; wait "$sampler" 2> /dev/null || true
    bench/timeseries.sh "$dir" "$start" "$(date +%s)"
    if [[ -n $has_mongo ]]; then
        printf 'phase,pages_read,bytes_read,app_reads,app_read_us\nbefore,%s\nafter,%s\n' \
            "$mongo_before" "$(mongo_cache)" > "$dir/mongo-cache.csv"
    fi

    local report
    report=$(ls -td bench/target/gatling/ledgersimulation-* | head -1)
    echo "${report#bench/target/gatling/}" > "$dir/gatling-report.txt"
    java bench/Report.java extract "$report" "$dir/stats.csv"
    java bench/Report.java seconds "$report" "$dir/seconds.csv"

    # 50 linhas: as instruções de um comando (summary.md, "comando no banco") não podem cair fora do corte quando
    # as leituras e as consultas do postgres-exporter dominam o tempo.
    psql_ -c "\copy (SELECT calls, round(total_exec_time) AS total_ms, round(mean_exec_time::numeric, 2) AS mean_ms,
                            round(stddev_exec_time::numeric, 2) AS stddev_ms, rows, shared_blks_hit, shared_blks_read,
                            round(shared_blk_read_time) AS read_ms,
                            left(regexp_replace(query, '\s+', ' ', 'g'), 400) AS query
                     FROM pg_stat_statements
                     WHERE dbid = (SELECT oid FROM pg_database WHERE datname = 'ledger') AND calls > 1
                     ORDER BY total_exec_time DESC LIMIT 50) TO STDOUT WITH CSV HEADER" > "$dir/pg_stat_statements.csv"

    local window="${DURATION}s"
    {
        echo "metric,value"
        echo "hikari_pending_max,$(prometheus "max(max_over_time(hikaricp_connections_pending[$window]))")"
        # Fila e conexões ativas somam os pools (no controle da #11 são dois; ADR-0010); com um pool, é o mesmo valor.
        echo "hikari_pending_avg,$(prometheus "sum(avg_over_time(hikaricp_connections_pending[$window]))")"
        echo "hikari_active_avg,$(prometheus "sum(avg_over_time(hikaricp_connections_active[$window]))")"
        echo "hikari_acquire_max_s,$(prometheus "max(max_over_time(hikaricp_connections_acquire_seconds_max[$window]))")"
        # Espera pelo pool x tempo com a conexão: separa a fila no pool da lentidão dentro do banco.
        echo "hikari_acquire_avg_s,$(prometheus "sum(increase(hikaricp_connections_acquire_seconds_sum[$window])) / sum(increase(hikaricp_connections_acquire_seconds_count[$window]))")"
        echo "hikari_usage_avg_s,$(prometheus "sum(increase(hikaricp_connections_usage_seconds_sum[$window])) / sum(increase(hikaricp_connections_usage_seconds_count[$window]))")"
        echo "app_cpu_avg,$(prometheus "avg(avg_over_time(process_cpu_usage[$window]))")"
        # Tempo total com a JVM parada em GC na execução; o contador atravessa lacunas de coleta (pausas longas).
        echo "gc_pause_total_s,$(prometheus "sum(increase(jvm_gc_pause_seconds_sum[$window]))")"
        echo "pg_blks_hit,$(prometheus "sum(increase(pg_stat_database_blks_hit{datname=\"ledger\"}[$window]))")"
        echo "pg_blks_read,$(prometheus "sum(increase(pg_stat_database_blks_read{datname=\"ledger\"}[$window]))")"
        # Por pool: no controle da #11, mostra se os comandos ainda esperam conexão quando as consultas fazem fila.
        prometheus_by_pool hikari_acquire_avg_s "sum by (instance, pool) (increase(hikaricp_connections_acquire_seconds_sum[$window])) / sum by (instance, pool) (increase(hikaricp_connections_acquire_seconds_count[$window]))"
        prometheus_by_pool hikari_usage_avg_s "sum by (instance, pool) (increase(hikaricp_connections_usage_seconds_sum[$window])) / sum by (instance, pool) (increase(hikaricp_connections_usage_seconds_count[$window]))"
        prometheus_by_pool hikari_pending_max "max by (instance, pool) (max_over_time(hikaricp_connections_pending[$window]))"
        # Por JVM: na v3, separa o heap e o GC da command-api dos da query-api (H3, ADR-0010).
        prometheus_by_instance gc_pause_total_s "sum by (instance) (increase(jvm_gc_pause_seconds_sum[$window]))"
        prometheus_by_instance app_cpu_avg "avg by (instance) (avg_over_time(process_cpu_usage[$window]))"
        prometheus_by_instance heap_max_mib "max by (instance) (max_over_time(sum by (instance) (jvm_memory_used_bytes{area=\"heap\"})[$window:5s])) / 1048576"
        # v3 (H5): atraso da projeção, reentregas e fila do outbox; vazias nas outras fases.
        echo "projection_lag_p50_s,$(prometheus "histogram_quantile(0.5, sum by (le) (increase(ledger_projection_lag_seconds_bucket[$window])))")"
        echo "projection_lag_p99_s,$(prometheus "histogram_quantile(0.99, sum by (le) (increase(ledger_projection_lag_seconds_bucket[$window])))")"
        echo "projection_lag_max_s,$(prometheus "max(max_over_time(ledger_projection_lag_seconds_max[$window]))")"
        echo "projection_applied,$(prometheus "sum(increase(ledger_projection_lag_seconds_count[$window]))")"
        echo "projection_duplicates,$(prometheus "sum(increase(ledger_projection_duplicates_total[$window]))")"
        echo "outbox_backlog_max,$(prometheus "max(max_over_time(ledger_outbox_backlog[$window]))")"
    } > "$dir/prometheus.csv"
}

max_read=$(tr ' ' '\n' <<< "$READ_RATES" | sort -n | tail -1)

log "aquecimento descartado: ${WARMUP}s com escrita $WRITE_RATE/s e leitura $max_read/s"
gatling "$WRITE_RATE" "$max_read" "$WARMUP" 1 > bench/target/warmup.log 2>&1 || { tail -40 bench/target/warmup.log >&2; exit 1; }

for rep in $(seq 1 "$REPS"); do
    run s1 "$WRITE_RATE" 0 "$rep"
    for rate in $READ_RATES; do
        run "s3-r$(printf '%03d' "$rate")" "$WRITE_RATE" "$rate" "$rep"
    done
    run s2 0 "$max_read" "$rep"
done

java bench/Report.java summarize "$OUT"

# Planos das consultas da fase, com o cache no estado em que a carga o deixou (ADR-0005, regra 5).
if [[ -f bench/explain/$PHASE.sql ]]; then
    log "EXPLAIN ANALYZE ($PHASE)"
    "${COMPOSE[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U ledger -d ledger -qAt \
        < "bench/explain/$PHASE.sql" > "$OUT/explain.md"
fi
log "pronto: $OUT/summary.md"
