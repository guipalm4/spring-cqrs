#!/usr/bin/env bash
# Dia de medição: as fases em sequência, no mesmo dia, cada uma com os volumes apagados e o dataset de referência
# recarregado (ADR-0005; ADR-0012 para a ordem e o S5). Leva ~8h com as cinco fases.
#
# Uso: bench/day.sh                       (v1 v1-big v2 v3 v3-s5)
#      PHASES="v2 v3" bench/day.sh        (refazer só algumas; a comparação só vale entre fases do mesmo dia)
#
# Para os containers de fora do projeto antes de começar e interrompe no primeiro erro. Os resultados vão para
# docs/benchmarks/<data do início>-<fase>/ (todas com a mesma data, mesmo passando da meia-noite) e o log de cada fase
# para bench/target/day-<data>/<fase>.log. Avisa no fim ou na falha com uma notificação do macOS.
set -euo pipefail

cd "$(dirname "$0")/.."

PHASES=${PHASES:-"v1 v1-big v2 v3 v3-s5"}
DAY=$(date +%F)
LOGS=bench/target/day-$DAY
PROJECT=${COMPOSE_PROJECT_NAME:-spring-cqrs}
ALL=(docker compose -f infra/compose.yaml --profile '*')
phase=preparação

log() { printf '\n[day %s] %s\n' "$(date +%T)" "$*"; }
notify() { osascript -e "display notification \"$1\" with title \"spring-cqrs\"" 2> /dev/null || true; }
fail() {
    log "$1"
    notify "$1"
    exit 1
}
trap 'fail "falhou na fase $phase (linha $LINENO); log em $LOGS/"' ERR

# --- Pré-condições: verificadas antes de apagar qualquer coisa ------------------------------------------------
git diff --quiet HEAD -- . ':!docs/benchmarks' || fail "há alterações locais: commite antes de medir"
for p in $PHASES; do
    [[ ! -e docs/benchmarks/$DAY-$p ]] || fail "docs/benchmarks/$DAY-$p já existe"
done
mkdir -p "$LOGS"
log "fases: $PHASES (commit $(git rev-parse --short HEAD), logs em $LOGS/)"

# Containers de fora do projeto disputam CPU e memória da VM com a medição: são parados (não removidos) e listados
# em $LOGS/stopped-containers.txt. Para voltar com eles: docker start $(cat $LOGS/stopped-containers.txt)
others=$(docker ps --format '{{.Names}}	{{.Label "com.docker.compose.project"}}' | awk -F'\t' -v p="$PROJECT" '$2 != p { print $1 }')
if [[ -n $others ]]; then
    log "parando containers de fora da medição: ${others//$'\n'/ }"
    echo "$others" > "$LOGS/stopped-containers.txt"
    # shellcheck disable=SC2086 # um nome por palavra
    docker stop $others > /dev/null
fi

for phase in $PHASES; do
    log "$phase: volumes apagados, subida e seed SCALE=1"
    {
        "${ALL[@]}" down -v --remove-orphans
        make up PHASE="$phase"
        make seed PHASE="$phase" SCALE=1
    } 2>&1 | tee "$LOGS/$phase.log"

    log "$phase: benchmark"
    # No S5 só a taxa que ele pergunta (ADR-0012); o S1 e o S2 saem junto, como referência.
    rates=$([[ $phase == v3-s5 ]] && echo 75 || echo "${READ_RATES:-25 50 75}")
    OUT=docs/benchmarks/$DAY-$phase READ_RATES=$rates make bench PHASE="$phase" 2>&1 | tee -a "$LOGS/$phase.log"
done

phase=encerramento
"${ALL[@]}" down
log "medição concluída: docs/benchmarks/$DAY-*"
notify "medição concluída ($PHASES)"
