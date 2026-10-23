PHASE ?= v1
COMPOSE := docker compose -f infra/compose.yaml --profile $(PHASE)
SCALE ?= 1
SALT ?= 42
PSQL = $(COMPOSE) exec -T postgres psql -v ON_ERROR_STOP=1 -U ledger -d ledger
V3 := $(filter v3 v3-s5,$(PHASE))

# make up PHASE=v3 TRACE=1: sobe o Tempo e soma o profile trace ao papel de cada JVM da v3 (ADR-0012). O make bench
# se recusa a medir nesse modo.
ifneq ($(TRACE),)
COMPOSE += --profile trace
export TRACE_PROFILE := ,trace
endif

# Controle do ADR-0012: o Postgres da v1-big tem os recursos e o cache dos dois stores da v3 somados.
ifeq ($(PHASE),v1-big)
export PG_CPUS := 4
export PG_MEM := 4g
export PG_SHARED_BUFFERS := 1GB
export PG_EFFECTIVE_CACHE_SIZE := 3GB
endif

.DEFAULT_GOAL := help
.PHONY: help verify up down clean ps logs smoke seed bench

help: ## Lista os alvos
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-8s %s\n", $$1, $$2}'

verify: ## Compila e roda todos os testes (Testcontainers)
	./mvnw -B -ntp verify

up: ## Sobe a fase: make up PHASE=v1|v1-split|v1-big|v2|v3|v3-s5 [TRACE=1] (ADR-0010, ADR-0012)
	$(COMPOSE) up -d --build --wait

down: ## Derruba a fase, mantendo o volume do Postgres
	$(COMPOSE) down

clean: ## Derruba a fase e apaga os volumes (Postgres e, na v3, Mongo)
	$(COMPOSE) down -v

ps: ## Estado dos containers da fase
	$(COMPOSE) ps

logs: ## Acompanha os logs da fase
	$(COMPOSE) logs -f

smoke: ## Confere se a aplicação da fase está saudável (na v3, command-api e query-api)
	@for port in 8080 $(if $(V3),8081) $(if $(filter v3-s5,$(PHASE)),8082); do \
	  for i in $$(seq 1 30); do \
	    curl -fsS localhost:$$port/actuator/health && echo && continue 2; sleep 2; \
	  done; echo "aplicação em :$$port não respondeu" >&2; exit 1; \
	done

seed: ## Carrega o dataset (APAGA os dados) e as projeções da v2 ou da v3: make seed SCALE=1 (ADR-0008, ADR-0012)
	$(PSQL) -q -f - < bench/seed/seed.sql
	$(PSQL) -c "CALL seed.load($(SCALE), $(SALT))" -c "VACUUM (ANALYZE) account" -c "VACUUM (ANALYZE) ledger_entry"
ifneq ($(V3),)
	@# Na v3, as projeções vivem no Mongo: o projetor as carrega do ledger, sem servidor web e sem consumir o Kafka.
	$(COMPOSE) run --rm --no-deps -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
	  -e SPRING_KAFKA_LISTENER_AUTO_STARTUP=false -e LEDGER_SEED_MONGO=true -e LEDGER_SEED_MONGO_EXIT=true projector
else
	$(PSQL) -q -f - < bench/seed/rebuild.sql
endif

bench: ## Benchmark da fase no ar: make bench PHASE=v1 (~1h20; ADR-0005, ADR-0009, ADR-0012)
	PHASE=$(PHASE) bench/run.sh
