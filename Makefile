PHASE ?= v1
COMPOSE := docker compose -f infra/compose.yaml --profile $(PHASE)
SCALE ?= 1
SALT ?= 42
PSQL = $(COMPOSE) exec -T postgres psql -v ON_ERROR_STOP=1 -U ledger -d ledger

.DEFAULT_GOAL := help
.PHONY: help verify up down clean ps logs smoke seed bench

help: ## Lista os alvos
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-8s %s\n", $$1, $$2}'

verify: ## Compila e roda todos os testes (Testcontainers)
	./mvnw -B -ntp verify

up: ## Sobe a fase: make up PHASE=v1|v1-split|v2 (v1-split: controle da #11, ADR-0010)
	$(COMPOSE) up -d --build --wait

down: ## Derruba a fase, mantendo o volume do Postgres
	$(COMPOSE) down

clean: ## Derruba a fase e apaga o volume do Postgres
	$(COMPOSE) down -v

ps: ## Estado dos containers da fase
	$(COMPOSE) ps

logs: ## Acompanha os logs da fase
	$(COMPOSE) logs -f

smoke: ## Confere se a aplicação da fase está saudável
	@for i in $$(seq 1 30); do \
	  curl -fsS localhost:8080/actuator/health && echo && exit 0; sleep 2; \
	done; echo "aplicação não respondeu" >&2; exit 1

seed: ## Carrega o dataset (APAGA os dados): make seed SCALE=1 — referência, ver ADR-0008
	$(PSQL) -q -f - < bench/seed/seed.sql
	$(PSQL) -c "CALL seed.load($(SCALE), $(SALT))" -c "VACUUM (ANALYZE) account" -c "VACUUM (ANALYZE) ledger_entry"

bench: ## Benchmark da fase no ar: make bench PHASE=v1 (~1 h; ADR-0005, ADR-0009)
	PHASE=$(PHASE) bench/run.sh
