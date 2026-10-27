#!/usr/bin/env bash
# Executado só na criação do volume. Libera conexões de replicação para a réplica da v1-replica (ADR-0013); nas outras
# fases, nenhuma réplica se conecta. O usuário ledger é superusuário e, portanto, pode replicar.
set -euo pipefail
echo "host replication all all scram-sha-256" >> "$PGDATA/pg_hba.conf"
