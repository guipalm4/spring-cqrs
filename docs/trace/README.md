# Trace ponta a ponta da v3 (2026-10-23)

Critério do M4: um comando seguido do HTTP da `command-api` até as duas escritas do projetor no Mongo, num único
trace. O tracing fica fora do benchmark (amostragem 0 no `make bench`, [ADR-0012](../adr/0012-v3-projecao-mongo-e-controle-de-hardware.md#tracing)).

## Como reproduzir

```bash
export COMPOSE_PROJECT_NAME=spring-cqrs-trace   # volumes próprios: não apaga o dataset de referência
make up PHASE=v3 TRACE=1                        # sobe o Tempo, com datasource no Grafana (:3000)
make seed PHASE=v3 SCALE=0.01
curl -X POST localhost:8080/accounts/<id>/deposits -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":1000,"currency":"BRL","category":"GROCERIES","description":"trace","counterparty":"Mercado"}'
curl localhost:3200/api/traces/<traceId>        # o mesmo trace no Grafana: Explore → Tempo → <traceId>
make clean PHASE=v3 TRACE=1
```

Os arquivos `.json` desta pasta são a resposta do Tempo para os dois traces abaixo.

## Um comando a quente ([d31fdb36…](d31fdb36953bc28a8b577993c5333ccd.json))

Início e duração em ms, relativos ao início do trace.

| Serviço | Span | Início | Duração |
|---|---|---:|---:|
| command-api | http post /accounts/{accountId}/deposits | 0 | 17.9 |
| command-api | &nbsp;&nbsp;ledger.entry-posted send | 19.3 | 13 |
| projector | &nbsp;&nbsp;&nbsp;&nbsp;ledger.entry-posted process | 32.4 | 10.2 |
| projector | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;update ledger.statement_view | 34.6 | 5.2 |
| projector | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;update | 35.9 | 3.7 |
| projector | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;update ledger.monthly_summary | 40 | 2.4 |
| projector | &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;update | 40.6 | 1.4 |

- O envio ao Kafka começa **depois** da resposta HTTP (19,3 ms contra 17,9 ms): é o listener assíncrono do Modulith,
  que publica após o commit. O contexto atravessa a fronteira assíncrona e o Kafka (o `process` do projetor é filho do
  `send`).
- Do início do comando até o lançamento estar nas duas projeções: ~43 ms neste trace. O atraso medido no benchmark
  (`ledger.projection.lag`, a partir do `occurred_at`) teve p50 de 13–16 ms; o trace inclui também a parte do
  comando antes do commit e o próprio custo da instrumentação.
- Os spans `update` internos são os comandos do driver do Mongo (`MongoTracing`); os externos, as operações do
  Spring Data.
- O JDBC não é instrumentado: a transação do comando no Postgres está dentro do span HTTP, sem detalhe.

## O primeiro comando depois da partida ([a85aee11…](a85aee1100f911e3d191847b379227cc.json))

O mesmo caminho a frio levou 765 ms, quase tudo em inicialização preguiçosa: 380 ms até o primeiro envio (o produtor
do Kafka conecta e busca metadados) e 200 ms no próprio envio. Não aparece no benchmark, que aquece as fases antes de
medir.

Tabela gerada com `jq` sobre o JSON do Tempo (serviço, nome, profundidade pelo `parentSpanId`, início e duração).
