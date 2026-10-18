PHASE ?= v1
COMPOSE := docker compose -f infra/compose.yaml --profile $(PHASE)

.DEFAULT_GOAL := help
.PHONY: help verify up down clean ps logs smoke

help: ## Lista os alvos
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-8s %s\n", $$1, $$2}'

verify: ## Compila e roda todos os testes (Testcontainers)
	./mvnw -B -ntp verify

up: ## Sobe a fase: make up PHASE=v1|v2
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
