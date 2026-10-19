#!/usr/bin/env bash
# Benchmark de uma fase: make bench PHASE=v1. Metodologia no ADR-0005; parâmetros no ADR-0009.
#
# Pré-requisitos: a fase no ar (make up PHASE=...) e o dataset carregado (make seed SCALE=1).
# Saída: docs/benchmarks/<data>-<fase>/ com env.md, summary.md e runs/<cenário>/r<n>/ (estatísticas do Gatling,
# pg_stat_statements, docker stats e métricas do Prometheus). Os relatórios HTML completos ficam em
# bench/target/gatling/.
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
BASE_URL=${BASE_URL:-http://localhost:8080}
PROMETHEUS=${PROMETHEUS:-http://localhost:9090}

COMPOSE=(docker compose -f infra/compose.yaml --profile "$PHASE")
ACCOUNTS=$PWD/bench/target/accounts.csv

psql_() { "${COMPOSE[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U ledger -d ledger -qAt "$@"; }
log() { printf '\n[bench %s] %s\n' "$(date +%T)" "$*"; }

# --- Pré-condições ---------------------------------------------------------------------------------------------
curl -fsS "$BASE_URL/actuator/health" > /dev/null || { echo "aplicação fora do ar: make up PHASE=$PHASE" >&2; exit 1; }
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
app=$("${COMPOSE[@]}" ps --format '{{.Service}}' | grep -E '^app-|-api$' | head -1)
{
    echo "# Ambiente — $PHASE, $(date '+%F %T %Z')"
    echo
    echo "## Execução"
    echo
    echo "- Commit: \`$(git rev-parse --short HEAD)\`$(git diff --quiet HEAD -- . ':!docs/benchmarks' || echo ' (com alterações locais)')"
    echo "- Dataset: $seed_accounts contas do seed (escala $scale), $(psql_ -c "SELECT to_char(count(*), 'FM999G999G999') FROM ledger_entry") lançamentos"
    echo "- Parâmetros: REPS=$REPS DURATION=${DURATION}s WARMUP=${WARMUP}s PAUSE=${PAUSE}s WRITE_RATE=$WRITE_RATE READ_RATES=\"$READ_RATES\""
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
            | awk '{ printf "| %s | %s | %.2f | %d MiB |\n", substr($1, 2), $2, $3 / 1e9, $4 / 1048576 }'
    done
    echo
    echo "## JVM ($app)"
    echo
    echo "- JVM_OPTS: \`$("${COMPOSE[@]}" exec -T "$app" sh -c 'echo "$JVM_OPTS"')\`"
    echo "- $("${COMPOSE[@]}" exec -T "$app" java -version 2>&1 | head -1)"
    echo
    echo "## Postgres"
    echo
    echo "- $(psql_ -c 'SELECT version()')"
    psql_ -c "SELECT '- ' || name || ' = ' || setting || coalesce(' ' || unit, '') FROM pg_settings
              WHERE name IN ('shared_buffers', 'effective_cache_size', 'work_mem', 'maintenance_work_mem',
                             'random_page_cost', 'max_connections', 'io_method', 'effective_io_concurrency')
              ORDER BY name"
} > "$OUT/env.md"

# --- Execuções -------------------------------------------------------------------------------------------------
gatling() { # writeRate readRate duration seed
    ./mvnw -B -ntp -q -f bench/pom.xml gatling:test -DbaseUrl="$BASE_URL" -Daccounts="$ACCOUNTS" \
        -DwriteRate="$1" -DreadRate="$2" -Dduration="$3" -Dseed="$4"
}

prometheus() { # expressão -> valor no instante atual
    curl -fsS --get "$PROMETHEUS/api/v1/query" --data-urlencode "query=$1" \
        | jq -r '.data.result[0].value[1] // "NaN"'
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
    local sampler=$!

    # Semente por repetição: cada repetição sorteia outras contas, e a mesma repetição sorteia as mesmas
    # contas em todas as fases.
    gatling "$write" "$read" "$DURATION" $((100 + rep)) > "$dir/gatling.log" 2>&1 || {
        kill "$sampler"; tail -40 "$dir/gatling.log" >&2; exit 1; }
    kill "$sampler"; wait "$sampler" 2> /dev/null || true

    local report
    report=$(ls -td bench/target/gatling/ledgersimulation-* | head -1)
    echo "${report#bench/target/gatling/}" > "$dir/gatling-report.txt"
    java bench/Report.java extract "$report" "$dir/stats.csv"

    psql_ -c "\copy (SELECT calls, round(total_exec_time) AS total_ms, round(mean_exec_time::numeric, 2) AS mean_ms,
                            round(stddev_exec_time::numeric, 2) AS stddev_ms, rows, shared_blks_hit, shared_blks_read,
                            round(shared_blk_read_time) AS read_ms,
                            left(regexp_replace(query, '\s+', ' ', 'g'), 400) AS query
                     FROM pg_stat_statements
                     WHERE dbid = (SELECT oid FROM pg_database WHERE datname = 'ledger') AND calls > 1
                     ORDER BY total_exec_time DESC LIMIT 25) TO STDOUT WITH CSV HEADER" > "$dir/pg_stat_statements.csv"

    local window="${DURATION}s"
    {
        echo "metric,value"
        echo "hikari_pending_max,$(prometheus "max(max_over_time(hikaricp_connections_pending[$window]))")"
        echo "hikari_pending_avg,$(prometheus "avg(avg_over_time(hikaricp_connections_pending[$window]))")"
        echo "hikari_active_avg,$(prometheus "avg(avg_over_time(hikaricp_connections_active[$window]))")"
        echo "hikari_acquire_max_s,$(prometheus "max(max_over_time(hikaricp_connections_acquire_seconds_max[$window]))")"
        # Espera pelo pool x tempo com a conexão: separa a fila no pool da lentidão dentro do banco.
        echo "hikari_acquire_avg_s,$(prometheus "sum(increase(hikaricp_connections_acquire_seconds_sum[$window])) / sum(increase(hikaricp_connections_acquire_seconds_count[$window]))")"
        echo "hikari_usage_avg_s,$(prometheus "sum(increase(hikaricp_connections_usage_seconds_sum[$window])) / sum(increase(hikaricp_connections_usage_seconds_count[$window]))")"
        echo "app_cpu_avg,$(prometheus "avg(avg_over_time(process_cpu_usage[$window]))")"
        echo "pg_blks_hit,$(prometheus "sum(increase(pg_stat_database_blks_hit{datname=\"ledger\"}[$window]))")"
        echo "pg_blks_read,$(prometheus "sum(increase(pg_stat_database_blks_read{datname=\"ledger\"}[$window]))")"
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
log "pronto: $OUT/summary.md"
