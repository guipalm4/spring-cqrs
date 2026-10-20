# Escopo — Ledger CQRS

Projeto de portfólio que demonstra, **com métricas reproduzíveis**, quando e quanto o pattern CQRS
ajuda num contexto financeiro — e quanto ele custa. A arquitetura evolui em fases, cada uma mudando
uma única variável, para que cada ganho (ou perda) tenha causa atribuível.

## Objetivo e não-objetivo

- **Objetivo:** evidenciar um problema real de um modelo único (leitura e escrita no mesmo Postgres),
  depois mostrar o efeito de separar o modelo (v2) e o armazenamento (v3), medindo benefício **e** custo.
- **Não-objetivo:** provar que CQRS é sempre melhor. Resultados desfavoráveis são publicados.
- **Custo zero:** toda ferramenta e serviço usa o plano gratuito como referência. Nada que exija plano
  pago entra no escopo, nem como opcional.

## Fases

| Fase | Variável alterada | Escrita | Leitura | Sincronização |
|---|---|---|---|---|
| **v1 — Baseline** | — | Postgres | Mesmo modelo, mesmo Postgres | — |
| **v2 — CQRS lógico** | Modelo | Postgres | Tabelas de projeção no mesmo Postgres | Síncrona, mesma transação |
| **v3 — CQRS físico** | Armazenamento + consistência | Postgres (`command-api`) | MongoDB (+ OpenSearch no M5) (`query-api`) | Assíncrona: outbox (Spring Modulith) → Kafka → projetores |

Na v3, comandos e consultas rodam em containers separados (`command-api` e `query-api`) a partir do
mesmo artefato, com profiles diferentes — o que permite escalar a leitura de forma independente.

Detalhes e justificativa: [ADR-0002](adr/0002-evolucao-em-fases.md).

## Domínio: livro-razão de conta digital

### Lado de escrita (comandos)

| Comando | Invariantes |
|---|---|
| `OpenAccount` | Conta única por titular + moeda |
| `Deposit` / `Withdraw` | Saque não ultrapassa saldo + limite |
| `Transfer` | Partida dobrada: débito e crédito somam zero, atômicos; contas na mesma moeda |

Regras transversais:

- Todo comando carrega **chave de idempotência**; reenvio devolve o resultado original.
- Dinheiro como value object (`long` em unidades menores + moeda). Sem `double`.
- Concorrência na conta via lock pessimista (`SELECT … FOR UPDATE`), com ordem de lock determinística
  em transferências para evitar deadlock.
- Lançamento (`ledger_entry`) é append-only e guarda `balance_after` — prática comum em ledgers e
  necessária para a baseline não ser um espantalho.

### Lado de leitura (consultas)

| ID | Consulta | Papel no experimento |
|---|---|---|
| **Q1** | Saldo atual da conta | **Grupo de controle** — barata em todas as fases; CQRS não deve ajudar |
| **Q2** | Extrato paginado por período, com categoria e contraparte | Joins + paginação sobre tabela grande |
| **Q3** | Resumo mensal por categoria (últimos 12 meses) | Agregação pesada sobre o ledger |
| **Q4** | Busca livre (descrição, contraparte) com filtros combinados | Busca textual; motivação do OpenSearch |

O **contrato REST é idêntico em todas as fases** — é o que permite rodar o mesmo teste de carga contra
v1, v2 e v3.

| Consulta | v1 | v2 | v3 (M4) | v3 (M5) |
|---|---|---|---|---|
| Q1 | `account.balance` | idem | idem (lado de escrita) | idem |
| Q2 | Joins sobre `ledger_entry` | Tabela `statement_view` desnormalizada | MongoDB (doc por conta/mês) | MongoDB |
| Q3 | `GROUP BY` sobre `ledger_entry` | Tabela `monthly_summary` pré-agregada | MongoDB | MongoDB |
| Q4 | `pg_trgm` + full-text (`tsvector`) | Coluna `tsvector` na projeção | MongoDB text index | **OpenSearch** |

Q4 no Mongo (M4) é deliberado: cria o ponto de comparação "store genérico × store especializado" que
justifica o OpenSearch no M5.

## Hipóteses (o que o benchmark deve confirmar ou refutar)

| # | Hipótese | Métrica |
|---|---|---|
| H1 | Sob carga mista, leituras Q2/Q3 degradam a latência de escrita na v1 | p95/p99 de comandos com e sem carga de leitura |
| H2 | Separar o modelo (v2) reduz o custo de Q3 de forma significativa; Q2 tem ganho menor | p95 por consulta, `pg_stat_statements` |
| H3 | Separar o armazenamento (v3) elimina a interferência leitura→escrita (H1) | p95/p99 de comandos sob carga mista |
| H4 | Q1 não melhora em nenhuma fase | p95 de Q1 |
| H5 | O custo da v3 é atraso de projeção mensurável e mais recursos | Atraso da projeção (p50/p99), CPU/RAM total, nº de containers |
| H6 | OpenSearch supera Mongo e Postgres em Q4 com filtros combinados | p95 de Q4 |
| H7 | Escalar só a `query-api` aumenta a vazão de leitura sem alterar a latência de escrita | Vazão de Q2/Q3 e p95 de comandos com 1 × 2 réplicas |

Metodologia: [ADR-0005](adr/0005-metodologia-de-benchmark.md).

## Fora de escopo

Event sourcing (CQRS ≠ ES; o ledger append-only lembra ES, mas o estado de escrita continua relacional),
câmbio/multi-moeda em transferências, autenticação real, sagas, microsserviços, Kubernetes, frontend,
alta disponibilidade/réplicas de Kafka. Na camada AWS (M7): SQS/SNS, Lambda, DynamoDB, CloudWatch,
Cognito e serviços gerenciados emulados (MSK, OpenSearch Service, DocumentDB) — ver
[ADR-0006](adr/0006-camada-aws-localstack.md).

## Marcos

Cada marco é publicável sozinho (tag + PR + capítulo da história no README).

| Marco | Entrega | Critério de pronto |
|---|---|---|
| **M0 — Fundação** | Multi-módulo Maven, compose com profiles, CI com Testcontainers, ADRs | `mvn verify` verde no CI; `docker compose --profile v1 up` sobe |
| **M1 — v1 funcional** | Comandos, Q1–Q4, idempotência, seeder, OpenAPI + Swagger UI | Testes de invariantes (concorrência em transferência) passam; seed de referência carregado; contrato publicado em `/v3/api-docs` |
| **M2 — Evidência** | Gatling, Grafana, resultados v1 com `EXPLAIN ANALYZE` | `make bench PHASE=v1` reproduz; H1 respondida em `docs/benchmarks/` |
| **M3 — v2** | Projeções no mesmo Postgres | Resultados v1×v2; H2 e H4 respondidas |
| **M4 — v3** | Outbox Modulith → Kafka → projetor Mongo; idempotência; métrica de atraso; `command-api` e `query-api` separados | Resultados v1×v2×v3; H3, H5 e H7 respondidas; trace ponta a ponta no Grafana |
| **M5 — OpenSearch** | Segunda projeção (consumer group próprio) servindo Q4 | H6 respondida; demonstra 1 modelo de escrita → N modelos de leitura |
| **M6 — Relato** | README comparativo, ADR "quando NÃO usar CQRS" | README conta a história com gráficos e links para cada tag |
| **M7 — Camada AWS** | LocalStack + Terraform (`tflocal`); API Gateway roteando comandos → `command-api` e consultas → `query-api`; credenciais no Secrets Manager; `docs/aws-mapping.md` | `make aws-up` provisiona tudo do zero; chamadas via gateway funcionam; limitações do emulador documentadas |

A tese do projeto está completa no M6. O M7 é um complemento: se o projeto parar antes dele, nada
fica pela metade.

**Stretch (fora do caminho crítico):** reconstrução de projeção a partir do ledger; Debezium como
alternativa ao outbox do Modulith; Structured Concurrency (preview) na composição de consultas;
KMS para criptografar dados pessoais na escrita com projeções guardando só dados mascarados;
exportação de extrato para S3 com URL pré-assinada.

## Estrutura do repositório

```
ledger-domain/   # Money, comandos, eventos (sealed), invariantes — sem Spring
app-baseline/    # v1 — congelado após M2
app-cqrs/        # v2 e v3 via profile (read-store=postgres | mongo; role=command | query na v3)
projector/       # consumidores Kafka → Mongo / OpenSearch (v3)
bench/           # Gatling (Java DSL) + seeder
infra/           # docker compose (profiles v1|v2|v3|search|aws), Prometheus, Grafana (dashboards como código)
infra/terraform/ # recursos AWS (API Gateway, Secrets Manager) aplicados via tflocal no LocalStack
docs/            # SCOPE, ADRs, benchmarks/
```

## Decisões

| ADR | Decisão |
|---|---|
| [0001](adr/0001-stack-base.md) | Stack base: Java 25, Spring Boot 4.1, Maven multi-módulo |
| [0002](adr/0002-evolucao-em-fases.md) | Evolução em três fases, uma variável por vez |
| [0003](adr/0003-stores-de-leitura.md) | Stores de leitura: MongoDB (Q2/Q3) e OpenSearch (Q4) |
| [0004](adr/0004-sincronizacao-outbox-kafka.md) | Sincronização: outbox do Spring Modulith → Kafka |
| [0005](adr/0005-metodologia-de-benchmark.md) | Metodologia de benchmark e observabilidade |
| [0006](adr/0006-camada-aws-localstack.md) | Camada AWS com LocalStack: API Gateway, Secrets Manager, Terraform |
| [0007](adr/0007-ambiente-de-execucao.md) | Ambiente de execução: Docker, imagens, profiles do compose, limites de recursos |
| [0008](adr/0008-contrato-v1-e-dataset.md) | Contrato REST, semântica de Q1–Q4 e dataset de referência (distribuição assimétrica) |
| [0009](adr/0009-revisao-da-baseline-e-parametros-do-benchmark.md) | Revisão da baseline v1 (estatística, GIN compostos, Postgres e JVM) e parâmetros do benchmark |
| [0010](adr/0010-controle-pools-separados.md) | Controle da #11: v1 com pools separados para comandos (2) e consultas (8), sem mudar a baseline |
