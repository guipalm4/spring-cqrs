#!/usr/bin/env bash
# Entrypoint da réplica streaming da v1-replica (ADR-0013). Na primeira subida (volume vazio), copia o primário com
# pg_basebackup, com um slot de replicação para o primário guardar o WAL que a réplica ainda não recebeu, e liga o
# hot_standby_feedback: consultas longas na réplica não são canceladas por conflito com a replicação, ao custo de
# segurar o vacuum no primário. Depois, segue o entrypoint oficial, que encontra o PGDATA pronto e só sobe o servidor
# com os mesmos parâmetros do primário (o command do compose).
#
# O slot fica no primário enquanto o volume dele existir: apagar só o volume da réplica deixa um slot sem leitor, que
# acumula WAL. O make clean apaga os dois.
set -euo pipefail

primary=postgres
slot=replica
export PGPASSWORD=$POSTGRES_PASSWORD

if [[ ! -s $PGDATA/PG_VERSION ]]; then
    until pg_isready -q -h "$primary" -U "$POSTGRES_USER"; do sleep 1; done
    mkdir -p "$(dirname "$PGDATA")"
    chown postgres:postgres "$(dirname "$PGDATA")"
    has_slot=$(psql -qAt -h "$primary" -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
        -c "SELECT count(*) FROM pg_replication_slots WHERE slot_name = '$slot'")
    create=()
    [[ $has_slot == 1 ]] || create=(--create-slot)
    gosu postgres env PGPASSWORD="$PGPASSWORD" pg_basebackup -h "$primary" -U "$POSTGRES_USER" -D "$PGDATA" \
        --write-recovery-conf --wal-method=stream --checkpoint=fast --slot="$slot" "${create[@]}"
    echo "hot_standby_feedback = on" >> "$PGDATA/postgresql.auto.conf"
fi

exec docker-entrypoint.sh "$@"
