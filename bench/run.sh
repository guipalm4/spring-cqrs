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
# Taxas fixas (ADR-0009; CAPACITY vazio). Cenários em SCENARIOS (padrão "s1 s3 s2"), mesma simulação, taxas diferentes:
#   s1               só escrita, WRITE_RATE comandos/s
#   s3               escrita constante + N consultas/s, um cenário s3-r<N> para cada N em READ_RATES (H1)
#   s2               só leitura, na maior taxa de READ_RATES
#   s1-sem-projetor  o s1 com o projetor da v3 parado: o Mongo não grava (diagnóstico da cauda, ADR-0013)
# As repetições são intercaladas (r1 de todos os cenários, depois r2...), para a deriva do cache e do
# autovacuum se espalhar entre os cenários em vez de cair toda num deles.
#
# Capacidade (ADR-0013; CAPACITY=c1 ou c2): uma escada de carga, com as REPS repetições de cada degrau em seguida.
#   c1-r<NNNN>  pico de leitura: escrita fixa em WRITE_RATE (20/s), leitura em cada N de READ_RATES
#               (padrão 75 150 300 600 1200)
#   c2-w<NNNN>  escrita intensa com busca: leitura fixa em READ_RATE (75/s), escrita em cada N de WRITE_RATES
#               (padrão 20 50 100 200 400 800)
# Depois de cada degrau, o bench/Report.java aplica o critério do ADR-0013 (Report.judge); a escada para no primeiro
# degrau não sustentado em pelo menos 2 das 3 repetições, ou limitado pelo gerador.
#
# Em todos os modos: o Grafana fica parado durante a medição (ADR-0013); a carga do host entra no env.md no início e
# no fim de cada execução; pg-seconds.csv guarda, a cada segundo, o WAL (bytes, escritas, fsyncs e seus tempos, do
# pg_stat_io) e, na v1-replica, o atraso da réplica; nas fases com fila (v3 e v1-replica), a execução seguinte só
# começa com a fila vazia.
set -euo pipefail

cd "$(dirname "$0")/.."

PHASE=${PHASE:-v1}
CAPACITY=${CAPACITY:-}
case $CAPACITY in
    "") ;;
    c1) READ_RATES=${READ_RATES:-"75 150 300 600 1200"} ;;
    c2) WRITE_RATES=${WRITE_RATES:-"20 50 100 200 400 800"} ;;
    *) echo "CAPACITY=$CAPACITY: use c1 ou c2" >&2; exit 1 ;;
esac
REPS=${REPS:-3}
DURATION=${DURATION:-180}
WARMUP=${WARMUP:-300}
PAUSE=${PAUSE:-20}
WRITE_RATE=${WRITE_RATE:-20}
READ_RATE=${READ_RATE:-75}
READ_RATES=${READ_RATES:-"25 50 75"}
WRITE_RATES=${WRITE_RATES:-}
SCENARIOS=${SCENARIOS:-"s1 s3 s2"}
# Espera máxima pela fila vazia entre execuções; se não esvaziar, a execução seguinte começa assim mesmo (e o log diz).
DRAIN_TIMEOUT=${DRAIN_TIMEOUT:-600}
COMMANDS_URL=${COMMANDS_URL:-http://localhost:8080}
case $PHASE in
    v3 | v1-replica) default_queries=http://localhost:8081 ;;
    v3-s5) default_queries=http://localhost:8081,http://localhost:8082 ;;
    *) default_queries=$COMMANDS_URL ;;
esac
QUERIES_URLS=${QUERIES_URLS:-$default_queries}
PROMETHEUS=${PROMETHEUS:-http://localhost:9090}

COMPOSE=(docker compose -f infra/compose.yaml --profile "$PHASE")
ACCOUNTS=$PWD/bench/target/accounts.csv

psql_() { "${COMPOSE[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U ledger -d ledger -qAt "$@"; }
replica_psql() { "${COMPOSE[@]}" exec -T postgres-replica psql -v ON_ERROR_STOP=1 -U ledger -d ledger -qAt "$@"; }
log() { printf '\n[bench %s] %s\n' "$(date +%T)" "$*"; }

# --- Pré-condições ---------------------------------------------------------------------------------------------
for url in "$COMMANDS_URL" ${QUERIES_URLS//,/ }; do
    curl -fsS "$url/actuator/health" > /dev/null || { echo "$url fora do ar: make up PHASE=$PHASE" >&2; exit 1; }
done
has_mongo=$("${COMPOSE[@]}" ps --format '{{.Service}}' | grep -qx mongo && echo 1 || true)
has_replica=$("${COMPOSE[@]}" ps --format '{{.Service}}' | grep -qx postgres-replica && echo 1 || true)
# Com o tracing ligado (make up TRACE=1), as JVMs da v3 pagam um custo que a v1 e a v2 não pagaram (ADR-0012).
if docker compose -f infra/compose.yaml --profile trace ps --format '{{.Service}}' | grep -qx tempo; then
    echo "o Tempo está no ar (TRACE=1): suba a fase sem TRACE para medir" >&2
    exit 1
fi
seed_accounts=$(psql_ -c "SELECT count(*) FROM account WHERE holder_id LIKE 'seed-%'")
[[ $seed_accounts -gt 0 ]] || { echo "dataset vazio: make seed SCALE=1" >&2; exit 1; }
scale=$(LC_ALL=C awk -v n="$seed_accounts" 'BEGIN { printf "%g", n / 100000 }')
OUT=${OUT:-docs/benchmarks/$(date +%F)-${CAPACITY:+$CAPACITY-}$PHASE$([[ $scale == 1 ]] || echo "-scale$scale")}
if [[ -e $OUT ]]; then
    echo "$OUT já existe; apague ou passe OUT=..." >&2
    exit 1
fi
mkdir -p "$OUT/runs" bench/target

# --- Fila da fase: v3 (outbox + lag do consumer group) e v1-replica (WAL ainda não aplicado na réplica) ------------
outbox_count() { psql_ -c 'SELECT count(*) FROM event_publication'; }

kafka_lag() { # lançamentos no tópico que o projetor ainda não consumiu
    # A CLI herdaria o heap do broker (KAFKA_HEAP_OPTS de 512 MB) num container de 1 GB.
    "${COMPOSE[@]}" exec -T -e KAFKA_HEAP_OPTS=-Xmx128m kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
        --describe --group projector-mongo 2> /dev/null \
        | awk '$2 == "ledger.entry-posted" && $6 ~ /^[0-9]+$/ { lag += $6 } END { print lag + 0 }'
}

replica_lag_bytes() {
    psql_ -c 'SELECT coalesce(max(pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn)), 0)::bigint FROM pg_stat_replication'
}

queue_now() { # 0 quando não há fila; na réplica, até 1 MB de WAL de fundo (autovacuum, checkpoint) conta como vazia
    if [[ -n $has_mongo ]]; then
        echo $(( $(outbox_count) + $(kafka_lag) ))
    elif [[ -n $has_replica ]]; then
        local bytes
        bytes=$(replica_lag_bytes)
        echo $(( bytes > 1048576 ? bytes : 0 ))
    else
        echo 0
    fi
}

drain() { # espera a fila esvaziar, para uma execução não herdar o atraso da anterior
    [[ -n $has_mongo || -n $has_replica ]] || return 0
    local deadline=$(( $(date +%s) + DRAIN_TIMEOUT )) queue
    while queue=$(queue_now); [[ $queue -gt 0 ]]; do
        if (( $(date +%s) >= deadline )); then
            log "a fila não esvaziou em ${DRAIN_TIMEOUT}s (ainda $queue); seguindo assim mesmo"
            return 0
        fi
        sleep 5
    done
}

# --- Grafana fora da medição (ADR-0013): a 512 MB, ele já fez GBs de I/O no disco compartilhado da VM -------------
if "${COMPOSE[@]}" ps --format '{{.Service}}' | grep -qx grafana; then
    log "parando o Grafana até o fim da medição"
    "${COMPOSE[@]}" stop grafana > /dev/null
    trap '"${COMPOSE[@]}" start grafana > /dev/null 2>&1 || true' EXIT
fi

if [[ -n $has_replica ]]; then
    log "esperando a réplica aplicar o WAL do seed"
    DRAIN_TIMEOUT=3600 drain
fi

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
    if [[ -n $CAPACITY ]]; then
        echo "- Capacidade $CAPACITY (ADR-0013): REPS=$REPS DURATION=${DURATION}s WARMUP=${WARMUP}s PAUSE=${PAUSE}s $(
            [[ $CAPACITY == c1 ]] && echo "WRITE_RATE=$WRITE_RATE READ_RATES=\"$READ_RATES\"" \
                                  || echo "READ_RATE=$READ_RATE WRITE_RATES=\"$WRITE_RATES\"")"
    else
        echo "- Parâmetros: REPS=$REPS DURATION=${DURATION}s WARMUP=${WARMUP}s PAUSE=${PAUSE}s WRITE_RATE=$WRITE_RATE READ_RATES=\"$READ_RATES\" SCENARIOS=\"$SCENARIOS\""
    fi
    echo "- Grafana parado durante a medição"
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
    if [[ -n $has_replica ]]; then
        echo
        echo "## Réplica"
        echo
        echo "- $(psql_ -c "SELECT 'estado ' || state || ', slot ' || coalesce((SELECT slot_name FROM pg_replication_slots LIMIT 1), '—') FROM pg_stat_replication")"
        echo "- $(replica_psql -c "SELECT 'hot_standby_feedback = ' || current_setting('hot_standby_feedback') || ', shared_buffers = ' || current_setting('shared_buffers')")"
    fi
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
    echo
    echo "## Carga do host"
    echo
    echo "Load average (1, 5 e 15 min) e memória livre do macOS, no início e no fim de cada execução (ADR-0013)."
    echo
    echo "| Execução | Momento | Load average | Memória livre |"
    echo "|---|---|---|---:|"
} > "$OUT/env.md"

# Limite de CPU de cada container: o Report.java separa o limite do gerador do limite da fase (ADR-0013).
{
    echo "container,cpus"
    for id in $("${COMPOSE[@]}" ps -q); do
        docker inspect -f '{{.Name}} {{.HostConfig.NanoCpus}}' "$id" | LC_ALL=C awk '{ printf "%s,%.2f\n", substr($1, 2), $2 / 1e9 }'
    done
} > "$OUT/containers.csv"

host_load() { # "| load1 load5 load15 | memória livre |"
    local load mem
    load=$(LC_ALL=C sysctl -n vm.loadavg 2> /dev/null | tr -d '{}' | xargs || true)
    mem=$(memory_pressure -Q 2> /dev/null | sed -n 's/.*percentage: //p' || true)
    echo "| ${load:-?} | ${mem:-?} |"
}

# --- Execuções -------------------------------------------------------------------------------------------------
gatling() { # writeRate readRate duration seed
    ./mvnw -B -ntp -q -f bench/pom.xml gatling:test -DcommandsUrl="$COMMANDS_URL" -DqueriesUrls="$QUERIES_URLS" \
        -Daccounts="$ACCOUNTS" -DwriteRate="$1" -DreadRate="$2" -Dduration="$3" -Dseed="$4"
}

# Contadores do cache do WiredTiger (cumulativos desde a partida do Mongo): a diferença entre o fim e o início da
# execução é o equivalente do shared_blks_read e do read_ms do Postgres. Os dois últimos são o que o WiredTiger
# gravou: bytes escritos a partir do cache e bytes escritos em disco pelo block manager (ADR-0013).
mongo_cache() {
    "${COMPOSE[@]}" exec -T mongo mongosh --quiet --eval '
        const w = db.serverStatus().wiredTiger, c = w.cache;
        print([c["pages read into cache"], c["bytes read into cache"],
               c["application threads page read from disk to cache count"],
               c["application threads page read from disk to cache time (usecs)"],
               c["bytes written from cache"], w["block-manager"]["bytes written"]].join(","))'
}

# Uma linha por segundo, de dentro do container do Postgres (um docker exec só): WAL gerado e as escritas e fsyncs do
# WAL com os seus tempos (pg_stat_io, track_wal_io_timing; ADR-0013) e, com réplica, o atraso dela. Os contadores do
# pg_stat_io são cumulativos e os processos os publicam com até ~1 s de atraso.
PG_SECONDS_SQL="SELECT extract(epoch FROM clock_timestamp())::bigint,
    (SELECT sum(writes) FROM pg_stat_io WHERE object = 'wal'),
    (SELECT round(sum(write_time)::numeric, 3) FROM pg_stat_io WHERE object = 'wal'),
    (SELECT sum(fsyncs) FROM pg_stat_io WHERE object = 'wal'),
    (SELECT round(sum(fsync_time)::numeric, 3) FROM pg_stat_io WHERE object = 'wal'),
    (SELECT wal_bytes FROM pg_stat_wal),
    (SELECT round(max(extract(epoch FROM replay_lag))::numeric, 3) FROM pg_stat_replication),
    (SELECT max(pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn))::bigint FROM pg_stat_replication)"

pg_sampler_start() { # arquivo
    echo "epoch_s,wal_writes,wal_write_ms,wal_fsyncs,wal_fsync_ms,wal_bytes,replay_lag_s,replay_lag_bytes" > "$1"
    "${COMPOSE[@]}" exec -T postgres bash -c 'echo $$ > /tmp/pg-sampler.pid
        while true; do psql -U ledger -d ledger -qAt -F, -c "$0"; sleep 1; done' "$PG_SECONDS_SQL" >> "$1" &
    pg_sampler=$!
}

pg_sampler_stop() {
    "${COMPOSE[@]}" exec -T postgres sh -c 'kill "$(cat /tmp/pg-sampler.pid)"' 2> /dev/null || true
    wait "$pg_sampler" 2> /dev/null || true
}

statements() { # psql (psql_ ou replica_psql) -> CSV das instruções da execução
    # 50 linhas: as instruções de um comando (summary.md, "comando no banco") não podem cair fora do corte quando
    # as leituras e as consultas do postgres-exporter dominam o tempo.
    "$1" -c "\copy (SELECT calls, round(total_exec_time) AS total_ms, round(mean_exec_time::numeric, 2) AS mean_ms,
                            round(stddev_exec_time::numeric, 2) AS stddev_ms, rows, shared_blks_hit, shared_blks_read,
                            round(shared_blk_read_time) AS read_ms,
                            left(regexp_replace(query, '\s+', ' ', 'g'), 400) AS query
                     FROM pg_stat_statements
                     WHERE dbid = (SELECT oid FROM pg_database WHERE datname = 'ledger') AND calls > 1
                     ORDER BY total_exec_time DESC LIMIT 50) TO STDOUT WITH CSV HEADER"
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
    if [[ -n $has_replica ]]; then
        replica_psql -c 'SELECT pg_stat_statements_reset()' > /dev/null || log "aviso: pg_stat_statements_reset() falhou na réplica"
    fi
    sleep "$PAUSE"
    echo "| $name r$rep | início $(host_load)" >> "$OUT/env.md"

    (while true; do
        docker stats --no-stream --format "{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.BlockIO}}" $("${COMPOSE[@]}" ps -q) \
            | sed "s/^/$(date +%s),/"
    done) > "$dir/docker-stats.csv" &
    local sampler=$! start mongo_before=""
    pg_sampler_start "$dir/pg-seconds.csv"
    start=$(date +%s)
    [[ -z $has_mongo ]] || mongo_before=$(mongo_cache)

    # Semente por repetição: cada repetição sorteia outras contas, e a mesma repetição sorteia as mesmas
    # contas em todas as fases.
    gatling "$write" "$read" "$DURATION" $((100 + rep)) > "$dir/gatling.log" 2>&1 || {
        kill "$sampler"; pg_sampler_stop; tail -40 "$dir/gatling.log" >&2; exit 1; }
    kill "$sampler"; wait "$sampler" 2> /dev/null || true
    pg_sampler_stop
    local end
    end=$(date +%s)
    echo "| $name r$rep | fim $(host_load)" >> "$OUT/env.md"
    # Fila no fim da execução (critério do ADR-0013), antes de ela esvaziar; o lag do Kafka leva alguns segundos.
    if [[ -n $has_mongo ]]; then
        printf 'metric,value\noutbox_end,%s\nkafka_lag_end,%s\n' "$(outbox_count)" "$(kafka_lag)" > "$dir/queue.csv"
    fi
    bench/timeseries.sh "$dir" "$start" "$end"
    if [[ -n $has_mongo ]]; then
        printf 'phase,pages_read,bytes_read,app_reads,app_read_us,bytes_written_from_cache,block_bytes_written\nbefore,%s\nafter,%s\n' \
            "$mongo_before" "$(mongo_cache)" > "$dir/mongo-cache.csv"
    fi

    local report
    report=$(ls -td bench/target/gatling/ledgersimulation-* | head -1)
    echo "${report#bench/target/gatling/}" > "$dir/gatling-report.txt"
    java bench/Report.java extract "$report" "$dir/stats.csv"
    java bench/Report.java seconds "$report" "$dir/seconds.csv"

    statements psql_ > "$dir/pg_stat_statements.csv"
    # Na v1-replica, as consultas rodam na réplica, que tem as próprias estatísticas.
    [[ -z $has_replica ]] || statements replica_psql > "$dir/pg_stat_statements-replica.csv"

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

    [[ $name != s1-sem-projetor ]] || "${COMPOSE[@]}" up -d --wait projector > /dev/null
    drain
}

# Degrau da escada (ADR-0013): 0 sustentado, 1 não sustentado, 2 limitado pelo gerador; o veredito vai para o log.
step_verdict() {
    local status=0
    java bench/Report.java step "$1" || status=$?
    return "$status"
}

if [[ -z $CAPACITY ]]; then
    max_read=$(tr ' ' '\n' <<< "$READ_RATES" | sort -n | tail -1)

    log "aquecimento descartado: ${WARMUP}s com escrita $WRITE_RATE/s e leitura $max_read/s"
    gatling "$WRITE_RATE" "$max_read" "$WARMUP" 1 > bench/target/warmup.log 2>&1 || { tail -40 bench/target/warmup.log >&2; exit 1; }
    drain

    for rep in $(seq 1 "$REPS"); do
        for scenario in $SCENARIOS; do
            case $scenario in
                s1) run s1 "$WRITE_RATE" 0 "$rep" ;;
                s3) for rate in $READ_RATES; do
                        run "s3-r$(printf '%03d' "$rate")" "$WRITE_RATE" "$rate" "$rep"
                    done ;;
                s2) run s2 0 "$max_read" "$rep" ;;
                s1-sem-projetor)
                    [[ -n $has_mongo ]] || { echo "s1-sem-projetor só existe na v3" >&2; exit 1; }
                    # Sem o projetor, o Kafka acumula os lançamentos; o run() o religa antes de esperar a fila esvaziar.
                    "${COMPOSE[@]}" stop projector > /dev/null
                    run s1-sem-projetor "$WRITE_RATE" 0 "$rep"
                    ;;
                *) echo "cenário desconhecido: $scenario" >&2; exit 1 ;;
            esac
        done
    done
else
    if [[ $CAPACITY == c1 ]]; then
        steps=$READ_RATES
    else
        steps=$WRITE_RATES
    fi
    first=${steps%% *}
    log "aquecimento descartado: ${WARMUP}s no primeiro degrau ($CAPACITY, $first/s)"
    if [[ $CAPACITY == c1 ]]; then
        gatling "$WRITE_RATE" "$first" "$WARMUP" 1 > bench/target/warmup.log 2>&1 || { tail -40 bench/target/warmup.log >&2; exit 1; }
    else
        gatling "$first" "$READ_RATE" "$WARMUP" 1 > bench/target/warmup.log 2>&1 || { tail -40 bench/target/warmup.log >&2; exit 1; }
    fi
    drain

    for rate in $steps; do
        if [[ $CAPACITY == c1 ]]; then
            name=c1-r$(printf '%04d' "$rate")
            for rep in $(seq 1 "$REPS"); do run "$name" "$WRITE_RATE" "$rate" "$rep"; done
        else
            name=c2-w$(printf '%04d' "$rate")
            for rep in $(seq 1 "$REPS"); do run "$name" "$rate" "$READ_RATE" "$rep"; done
        fi
        if ! step_verdict "$OUT/runs/$name"; then
            log "escada encerrada em $name"
            break
        fi
    done
fi

java bench/Report.java summarize "$OUT"

# Planos das consultas da fase, com o cache no estado em que a carga o deixou (ADR-0005, regra 5).
if [[ -f bench/explain/$PHASE.sql ]]; then
    log "EXPLAIN ANALYZE ($PHASE)"
    "${COMPOSE[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U ledger -d ledger -qAt \
        < "bench/explain/$PHASE.sql" > "$OUT/explain.md"
fi
log "pronto: $OUT/summary.md"
