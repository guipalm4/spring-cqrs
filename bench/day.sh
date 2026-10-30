#!/usr/bin/env bash
# Dia de medição: as fases em sequência, no mesmo dia, cada uma com os volumes apagados e o dataset de referência
# recarregado (ADR-0005; ADR-0012 para a ordem e o S5). Leva ~8h com as cinco fases.
#
# Uso: bench/day.sh                       (taxas fixas: v1 v1-big v2 v3 v3-s5)
#      PHASES="v2 v3" bench/day.sh        (refazer só algumas; a comparação só vale entre fases do mesmo dia)
#      CAPACITY=c1 bench/day.sh           (escada do pico de leitura: v1 v1-big v1-replica v2 v3 v3-s5; ADR-0013)
#      CAPACITY=c2 bench/day.sh           (escada da escrita intensa: v1 v1-big v1-replica v2 v3)
#
# Na escada do C1, a v3-s5 começa no degrau em que a v3 parou (H7); se a v3 sustentou todos, ou parou no gerador, a
# v3-s5 não roda. Pela regra de parada, um dia de escada leva de 6 a 9 h.
#
# Para os containers de fora do projeto antes de começar e interrompe no primeiro erro. Os resultados vão para
# docs/benchmarks/<data do início>-<fase>/ (todas com a mesma data, mesmo passando da meia-noite) e o log de cada fase
# para bench/target/day-<data>/<fase>.log. Avisa no fim ou na falha com uma notificação do macOS.
set -euo pipefail

cd "$(dirname "$0")/.."

CAPACITY=${CAPACITY:-}
case $CAPACITY in
    "") default_phases="v1 v1-big v2 v3 v3-s5" ;;
    c1) default_phases="v1 v1-big v1-replica v2 v3 v3-s5" ;;
    c2) default_phases="v1 v1-big v1-replica v2 v3" ;;
    *) echo "CAPACITY=$CAPACITY: use c1 ou c2" >&2; exit 1 ;;
esac
PHASES=${PHASES:-$default_phases}
# Ensaio do dia inteiro, fora do estudo: COMPOSE_PROJECT_NAME=spring-cqrs-ensaio SCALE=0.01 ROOT=<pasta> REPS=1 ...
SCALE=${SCALE:-1}
ROOT=${ROOT:-docs/benchmarks}
DAY=$(date +%F)
PREFIX=$ROOT/$DAY-${CAPACITY:+$CAPACITY-}
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
    [[ ! -e $PREFIX$p ]] || fail "$PREFIX$p já existe"
done
mkdir -p "$LOGS"
log "fases: $PHASES${CAPACITY:+, escada $CAPACITY} (commit $(git rev-parse --short HEAD), logs em $LOGS/)"

# Containers de fora do projeto disputam CPU e memória da VM com a medição: são parados (não removidos) e listados
# em $LOGS/stopped-containers.txt. Para voltar com eles: docker start $(cat $LOGS/stopped-containers.txt)
others=$(docker ps --format '{{.Names}}	{{.Label "com.docker.compose.project"}}' | awk -F'\t' -v p="$PROJECT" '$2 != p { print $1 }')
if [[ -n $others ]]; then
    log "parando containers de fora da medição: ${others//$'\n'/ }"
    echo "$others" > "$LOGS/stopped-containers.txt"
    # shellcheck disable=SC2086 # um nome por palavra
    docker stop $others > /dev/null
fi

failed=""
for phase in $PHASES; do
    # Taxas da fase: no S5 a taxa que ele pergunta (ADR-0012), com o S1 e o S2 junto, como referência; na escada do
    # C1, os degraus a partir de onde a v3 parou (ADR-0013).
    rates=${READ_RATES:-}
    if [[ $phase == v3-s5 && $CAPACITY == c1 ]]; then
        [[ -d ${PREFIX}v3 ]] || fail "a v3-s5 do C1 depende da v3 do mesmo dia (${PREFIX}v3)"
        stop=$(java bench/Report.java stopped-at "${PREFIX}v3")
        if [[ -z $stop ]]; then
            log "v3-s5: pulada, a v3 sustentou todos os degraus ou parou no gerador (H7 sem degrau para testar)"
            continue
        fi
        rates=$(tr ' ' '\n' <<< "${READ_RATES:-75 150 300 600 1200}" | awk -v s="$stop" '$1 >= s' | xargs)
    elif [[ $phase == v3-s5 && -z $CAPACITY ]]; then
        rates=75
    elif [[ -z $CAPACITY ]]; then
        rates=${rates:-"25 50 75"}
    fi

    # Uma fase que falha não derruba as seguintes (29/10): o dia segue, e a falha vai para o log e para a notificação.
    log "$phase: volumes apagados, subida e seed SCALE=$SCALE"
    if ! {
        "${ALL[@]}" down -v --remove-orphans
        make up PHASE="$phase"
        make seed PHASE="$phase" SCALE="$SCALE"
    } 2>&1 | tee "$LOGS/$phase.log"; then
        log "$phase: falhou na subida ou no seed; seguindo para a próxima fase"
        failed+=" $phase"
        continue
    fi

    log "$phase: benchmark${rates:+ (leitura: $rates)}"
    if ! OUT=$PREFIX$phase READ_RATES=$rates CAPACITY=$CAPACITY make bench PHASE="$phase" 2>&1 \
        | tee -a "$LOGS/$phase.log"; then
        log "$phase: o benchmark falhou; seguindo para a próxima fase"
        failed+=" $phase"
    fi
done

phase=encerramento
"${ALL[@]}" down
if [[ -n $CAPACITY ]] && ! java bench/Report.java capacity "$PREFIX"* | tee "$LOGS/capacity-$CAPACITY.md"; then
    log "aviso: a tabela de capacidade não foi gerada"
fi
[[ -z $failed ]] || fail "medição concluída com falha em:$failed (log em $LOGS/)"
log "medição concluída: $PREFIX*"
notify "medição concluída ($PHASES${CAPACITY:+, $CAPACITY})"
